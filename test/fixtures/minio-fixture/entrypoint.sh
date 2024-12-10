#!/bin/sh

set -e

# Create the bucket directory after tmpfs is mounted
mkdir -p /minio/data/bucket

# Start MinIO server
exec minio "$@"
