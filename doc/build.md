Building From Source
====================

This guide will walk you through the process of building Bitsquare from source.

> _**NOTE:** For most users, building from source is not necessary. See the [releases page](https://github.com/bitsquare/bitsquare/releases), where you'll find installers for Windows, Linux and Mac OS X._


For the impatient
-----------------

What follows is explained in detail in the sections below, but for those who know their way around Java, git and Maven, here are the instructions in a nutshell:

    $ javac -version
    javac 1.8.0_40       # must be 1.8.0_40 or better

    $ git clone https://github.com/bitsquare/bitsquare.git
    $ cd bitsquare
    $ mvn package    

When the build completes, you will find an executable jar: `gui/target/shaded.jar`. 
To run it use:
    $ java -jar gui/target/shaded.jar

To build the binary check out the build scripts under the package directory.

Prerequisites
-------------

The only prerequisite for building Bitsquare is installing the Java Development Kit (JDK), version 8u40 or better (as well as maven and git).

To check the version of Java you currently have installed:

    $ javac -version
    javac 1.8.0_40

If `javac` is not found, or your version is anything less than `1.8.0_40`, then you'll need to [download and install the latest JDK]( http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) for your platform.

> _**TIP:** Here are [instructions](http://www.webupd8.org/2014/03/how-to-install-oracle-java-8-in-debian.html) for installing the JDK via `apt` on Debian/Ubuntu systems._


Steps
-----

### 1. Get the source

The preferred approach is to clone the Bitsquare repository using [git](http://www.git-scm.com/):

    git clone https://github.com/bitsquare/bitsquare.git

However, if you're not familiar with git or it is otherwise inconvenient to use, you can also download and extract a zip file of the latest sources at https://github.com/bitsquare/bitsquare/archive/master.zip.


### 2. Build jar

Bitsquare uses maven as a build system. 

    $ cd bitsquare
    $ mvn package
    

### 3. Run

When the build completes, you will find an executable jar: `gui/target/shaded.jar`. 
To run it use:

    $ java -jar gui/target/shaded.jar
    
Please note that testnet is the default bitcoin network. Mainnet is disabled at that stage of development and will be activated as soon there is more testing done.
    
### 4. Development mode
  
Please check out our wiki for more information about [testing](https://github.com/bitsquare/bitsquare/wiki/Guide-for-testing-Bitsquare)
and how to use [regtest](https://github.com/bitsquare/bitsquare/wiki/How-to-use-Bitsquare-with-regtest-%28advanced%29)

Here are example program arguments for using regtest with localhost environment (not via Tor):  
    
    $ java -jar seednode/target/SeedNode.jar localhost:2002 2 50 true   
   
    $ java -jar gui/target/shaded.jar --useLocalhost=true --node.port=2222 --devTest=true --app.name=Bitsquare-Local-Regtest-Arbitrator  
    
    $ java -jar gui/target/shaded.jar --bitcoin.network=regtest --node.port=3332 --useLocalhost=true --devTest=true --app.name=Bitsquare-Local-Regtest-Alice  
   
    $ java -jar gui/target/shaded.jar --bitcoin.network=regtest --node.port=4442 --useLocalhost=true --devTest=true --app.name=Bitsquare-Local-Regtest-Bob   
  
  
### 5. Running local seed node with Tor

If you want to run locally a seed node via Tor you need to add your seed node's hidden service address to the SeedNodesRepository.java class.
You can find the hidden service address after you started once a seed node. Start it with a placeholder address like: 
   
    $ java -jar seednode/target/SeedNode.jar xxxxxxx.onion:8002 2 50 
    
Once the hidden service is published (check console output) quit the seed node and copy the hidden service address from the console output. 
Alternatively you can navigate to the application directory and open Bitsquare_seed_node_xxxxxxx.onion_8002/tor/hiddenservice/hostname.
use that hidden service address also to rename the xxxxxxx placeholder of your Bitsquare_seed_node_xxxxxxx.onion_8002 directory.
Start again the SeedNode.jar now with the correct hidden service address.
              
Here are example program arguments for using regtest and using the Tor network:  
    
    $ java -jar seednode/target/SeedNode.jar rxdkppp3vicnbgqt.onion:8002 2 50  
   
    $ java -jar gui/target/shaded.jar --bitcoin.network=regtest node.port=2222 --devTest=true --app.name=Bitsquare-Tor-Regtest-Arbitrator  
    
    $ java -jar gui/target/shaded.jar --bitcoin.network=regtest node.port=3332 --devTest=true --app.name=Bitsquare-Tor-Regtest-Alice  
   
    $ java -jar gui/target/shaded.jar --bitcoin.network=regtest node.port=4442 --devTest=true --app.name=Bitsquare-Tor-Regtest-Bob   
   

### 6. Enable unlimited Strength for cryptographic keys

Bitsquare uses 256 bit length keys which are still not permitted by default.  
Get around that ridiculous fact by adding the missing [jars from Oracle](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html).
Copy the BountyCastle provider jar file (bcprov-jdk15on-1.53.jar) from you local maven repository (/home/.m2) to $JavaHome/jre/lib/ext (to avoid getting 
a "JCE cannot authenticate the provider BC" exception).


Problems?
---------

If the instructions above don't work for you, please [raise an issue](https://github.com/bitsquare/bitsquare/issues/new?labels=%5Bbuild%5D). Thanks!
