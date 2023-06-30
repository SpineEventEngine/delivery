This module contains integration tests for Liquor started in distributed mode. This test is intended
to prove that Liquor is able to work correctly using distributed storage. This test starts several
Liquor servers that are connected to each other using distributed storage. Then the test performs 
some operations on different servers to prove that changes made on one server is visible for other
servers.

In current implementation the test requires a custom base image for the Liquor container to
perform some network settings for each container to simulate latency. 
