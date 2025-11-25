#!/bin/bash

# Read the JSON input from stdin
input=$(cat)

# Check if the request path is /findAvailableProducts
request_path=$(echo "$input" | jq -r '.["http-request"].path // empty')

if [ "$request_path" != "/products" ]; then
  echo "Hook: No changes made - path is not /products (path: $request_path)" >&2
  echo "$input"
  exit 0
fi

# Check if response body is an array
body_type=$(echo "$input" | jq -r '.["http-response"].body | type')

if [ "$body_type" != "array" ]; then
  echo "Hook: No changes made - response body is not an array (type: $body_type)" >&2
  echo "$input"
  exit 0
fi

# Check if response body array has elements with createdOn field
has_created_on=$(echo "$input" | jq -r '.["http-response"].body | length > 0 and (.[0] | has("createdOn"))')

if [ "$has_created_on" != "true" ]; then
  echo "Hook: No changes made - response body array is empty or elements do not have 'createdOn' field" >&2
  echo "$input"
  exit 0
fi

# Extract to-date from the request query parameters
to_date=$(echo "$input" | jq -r '.["http-request"].query["to-date"] // empty')

# If to-date is not found or is null, use current date
if [ -z "$to_date" ] || [ "$to_date" = "null" ]; then
  echo "Hook: Using current date as 'to-date' (not provided in request)" >&2
  # Use current date in YYYY-MM-DD format
  to_date=$(date -u "+%Y-%m-%d")
fi

# Normalize the date format - extract just the date portion (YYYY-MM-DD)
# Handle formats like "2025-10-15", "2025-10-15T10:30:00Z", etc.
to_date_normalized=$(echo "$to_date" | sed -E 's/T.*//' | grep -oE '^[0-9]{4}-[0-9]{2}-[0-9]{2}')

# Calculate to-date minus 1 day
# Detect which date implementation we're using
if date --version 2>&1 | grep -q "BusyBox"; then
  # BusyBox date (common in Docker/Alpine)
  echo "Hook: Using BusyBox date to parse: $to_date_normalized" >&2

  # Convert YYYY-MM-DD to epoch seconds, subtract 1 day (86400 seconds), convert back
  epoch=$(date -u -D "%Y-%m-%d" -d "$to_date_normalized" +%s 2>&1)
  parse_result=$?

  if [ $parse_result -eq 0 ]; then
    # Subtract 1 day (86400 seconds)
    new_epoch=$((epoch - 86400))
    # Convert back to YYYY-MM-DD format
    created_on=$(date -u -d "@$new_epoch" "+%Y-%m-%d" 2>&1)
    parse_result=$?
    if [ $parse_result -ne 0 ]; then
      echo "Hook: BusyBox date formatting failed: $created_on" >&2
      created_on=""
    fi
  else
    echo "Hook: BusyBox date parsing failed: $epoch" >&2
    created_on=""
  fi
elif date --version >/dev/null 2>&1; then
  # GNU date (Linux)
  echo "Hook: Using GNU date to parse: $to_date_normalized" >&2
  created_on=$(date -d "$to_date_normalized - 1 day" "+%Y-%m-%d" 2>&1)
  parse_result=$?
  if [ $parse_result -ne 0 ]; then
    echo "Hook: GNU date parsing failed with exit code $parse_result: $created_on" >&2
    created_on=""
  fi
else
  # BSD date (macOS)
  echo "Hook: Using BSD date to parse: $to_date_normalized" >&2
  created_on=$(date -v-1d -j -f "%Y-%m-%d" "$to_date_normalized" "+%Y-%m-%d" 2>&1)
  parse_result=$?
  if [ $parse_result -ne 0 ]; then
    echo "Hook: BSD date parsing failed with exit code $parse_result: $created_on" >&2
    created_on=""
  fi
fi

# If date parsing failed, this is an error
if [ -z "$created_on" ]; then
  echo "Hook: ERROR - failed to parse date: $to_date (normalized: $to_date_normalized)" >&2
  echo "$input"
  exit 1
fi

# Update the createdOn field in the response body
# The response body is an array of products
echo "Hook: Updating createdOn in array elements from '$to_date - 1 day' = '$created_on'" >&2
output=$(echo "$input" | jq --arg created_on "$created_on" '
  .["http-response"].body |= map(
    if has("createdOn") then
      .createdOn = $created_on
    else
      .
    end
  )
')

# Output the modified JSON
echo "$output"
