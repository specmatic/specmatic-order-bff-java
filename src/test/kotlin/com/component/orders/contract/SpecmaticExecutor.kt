package com.component.orders.contract

import org.assertj.core.api.Assertions
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal class SpecmaticExecutor(arg: String) {
    private val builder: ProcessBuilder
    private val command: String
    private var process: Process? = null
    private var stdOut: Thread? = null
    private var stdErr: Thread? = null

    private val logs = StringBuilder(8192)

    init {
        require(arg.isNotEmpty()) { "At least one argument is required to execute Specmatic" }
        command = "Specmatic-Enterprise $arg"
        try {
            val cmd = buildList {
                add("java")
                add("-jar")
                add("${System.getProperty("user.home")}/.specmatic/specmatic-enterprise.jar")
                add(arg)
            }
            builder = ProcessBuilder(cmd)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @Throws(Exception::class)
    fun start() {
        println("Starting $command")
        process = builder.start()
        this.stdOut = startStreamThread(process!!.inputStream, System.out, "$command:STDOUT")
        this.stdErr = startStreamThread(process!!.errorStream, System.err, "$command:STDERR")
    }

    @Throws(Exception::class)
    fun stop() {
        println("Stopping $command")
        val current = process ?: return

        // wait up to 10s, then forcibly destroy
        if (!current.waitFor(10, TimeUnit.SECONDS)) {
            try {
                current.destroy()
                current.waitFor(1, TimeUnit.SECONDS)
            } catch (_: Exception) {
            }
            if (current.isAlive) {
                current.destroyForcibly()
                current.waitFor(5, TimeUnit.SECONDS)
            }
        }

        // ensure reader threads finish
        stdOut?.join(1000)
        stdErr?.join(1000)
    }

    @Throws(Exception::class)
    fun verifySuccessfulExecutionWithNoFailures() {
        println("Verifying $command completed without failures")
        val current = checkNotNull(process) { "$command process has not been started" }
        current.waitFor()
        val exitCode = current.exitValue()
        Assertions.assertThat(exitCode)
            .withFailMessage(
                String.format("Expected %s to exit without any failures, but it exited with code %%d", command),
                exitCode
            )
            .isEqualTo(0)
        val hasSucceeded = logs.toString().contains("Failures: 0")
        Assertions.assertThat(hasSucceeded)
            .withFailMessage("Expected $command to report 0 failures but some tests have failed")
            .isTrue()
    }

    private fun startStreamThread(`in`: InputStream, out: PrintStream, label: String): Thread {
        val t = Thread {
            try {
                InputStreamReader(`in`, StandardCharsets.UTF_8).buffered().useLines { lines ->
                    lines.forEach { line ->
                        val entry = String.format("[%s] %s%s", label, line, System.lineSeparator())
                        logs.append(entry)
                        out.println(line)
                    }
                }
            } catch (_: Exception) {
            }
        }
        t.isDaemon = true
        t.start()
        return t
    }
}
