package com.component.orders.contract

import org.assertj.core.api.Assertions
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

internal class SpecmaticExecutor(command: String) {
    private val builder: ProcessBuilder
    private val commandName: String
    private var process: Process? = null
    private var stdOut: Thread? = null
    private var stdErr: Thread? = null

    private val logs = StringBuilder(8192)

    init {
        require(command.isNotBlank()) { "Which Specmatic command do you want to run?" }
        commandName = "Specmatic-Enterprise $command"
        try {
            val jarPath = Paths.get(System.getProperty("user.home"), ".specmatic", "specmatic-enterprise.jar")
            require(Files.isRegularFile(jarPath)) {
                "Specmatic Enterprise jar not found at ${jarPath.toAbsolutePath()}. " +
                    "Please visit https://docs.specmatic.io/download/#specmatic-enterprise to fetch the jar."
            }
            val cmd = buildList {
                add("java")
                add("-jar")
                add(jarPath.toString())
                add(command)
            }
            builder = ProcessBuilder(cmd)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @Throws(Exception::class)
    fun start() {
        println("Starting $commandName")
        process = builder.start()
        this.stdOut = startStreamThread(process!!.inputStream, System.out, "$commandName:STDOUT")
        this.stdErr = startStreamThread(process!!.errorStream, System.err, "$commandName:STDERR")
    }

    @Throws(Exception::class)
    fun stop() {
        println("Stopping $commandName")
        val current = process ?: return

        // wait up to 10s, then forcibly destroy
        if (!current.waitFor(10, TimeUnit.SECONDS)) {
            try {
                current.destroy()
                current.waitFor(5, TimeUnit.SECONDS)
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
        println("Verifying $commandName completed without failures")
        val current = checkNotNull(process) { "$commandName process has not been started" }
        current.waitFor()
        val exitCode = current.exitValue()
        Assertions.assertThat(exitCode)
            .withFailMessage(
                String.format("Expected %s to exit without any failures, but it exited with code %%d", commandName),
                exitCode
            )
            .isEqualTo(0)
        val hasSucceeded = logs.toString().contains("Failures: 0")
        Assertions.assertThat(hasSucceeded)
            .withFailMessage("Expected $commandName to report 0 failures but some tests have failed")
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
