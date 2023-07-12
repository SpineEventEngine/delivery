This module contains integration tests for Liquor started in distributed mode.

Such tests are intended to prove that Liquor is able to work correctly using
distributed storage. In particular, during the test execution, several Liquor instances
are started, and configured to have a common distributed storage.

Then some interaction is performed with Liquor instances, analysing the outcome
and comparing it to one which would normally follow as if there was just a single Liquor instance.

In current implementation tests require a custom base image for the Liquor container to
perform some network settings for each container to simulate latency issues. 
