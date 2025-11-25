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
  # Use current date in ISO 8601 format
  if date --version >/dev/null 2>&1; then
    # GNU date (Linux)
    to_date=$(date -Iseconds)
  else
    # BSD date (macOS)
    to_date=$(date -u "+%Y-%m-%dT%H:%M:%SZ")
  fi
fi

# Normalize the date format
# Remove microseconds if present (e.g., .661223)
to_date_normalized=$(echo "$to_date" | sed -E 's/(\.[0-9]+)?(Z|[+-][0-9]{2}:[0-9]{2})?$//')

# Add Z suffix if no timezone is present
if ! echo "$to_date" | grep -qE '(Z|[+-][0-9]{2}:[0-9]{2})$'; then
  to_date_normalized="${to_date_normalized}Z"
else
  # Keep the original timezone
  timezone=$(echo "$to_date" | grep -oE '(Z|[+-][0-9]{2}:[0-9]{2})$')
  to_date_normalized="${to_date_normalized}${timezone}"
fi

# Calculate to-date minus 1 day
if date --version >/dev/null 2>&1; then
  # GNU date (Linux)
  created_on=$(date -d "$to_date_normalized - 1 day" -Iseconds 2>/dev/null)
else
  # BSD date (macOS)
  # Try parsing with Z suffix first
  created_on=$(date -v-1d -j -f "%Y-%m-%dT%H:%M:%SZ" "$to_date_normalized" "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null)

  # If that fails, try with timezone offset format
  if [ -z "$created_on" ]; then
    to_date_clean=$(echo "$to_date_normalized" | sed 's/Z$/+00:00/')
    created_on=$(date -v-1d -j -f "%Y-%m-%dT%H:%M:%S%z" "$to_date_clean" "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null)
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
