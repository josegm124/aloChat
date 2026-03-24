FROM confluentinc/cp-kafka:7.6.1

ENTRYPOINT ["/bin/bash", "-lc"]
CMD ["echo ready && sleep 1"]
