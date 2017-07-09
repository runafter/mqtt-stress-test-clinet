MQTT Client Cluster
===================
[![Build Status](https://travis-ci.org/runafter/mqtt-stress-test-clinet.svg?branch=master)](https://travis-ci.org/runafter/mqtt-stress-test-clinet)

# MQTT Server
## mosquitto
- http://mosquitto.org/

```
cd server/eclipse-mosquitto
vagrant box add ubuntu/xenial64
vagrant up
```

# ETCD for clients

```
cd client-etcd
vagrant box add ubuntu/xenial64
vagrant up
```