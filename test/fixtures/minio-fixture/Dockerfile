FROM minio/minio:RELEASE.2022-11-17T23-20-09Z

ARG bucket
ARG accessKey
ARG secretKey

ENV MINIO_ROOT_USER=${accessKey}
ENV MINIO_ROOT_PASSWORD=${secretKey}
ENV bucket=${bucket}

COPY entrypoint.sh /usr/bin/entrypoint.sh
RUN chmod +x /usr/bin/entrypoint.sh

ENTRYPOINT ["/usr/bin/entrypoint.sh"]
CMD ["server", "--console-address", ":9001", "/minio/data"]
