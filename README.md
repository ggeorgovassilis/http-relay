# http-relay
A transparent HTTP relay used to access web applications behind a firewall. This is work in progress not intended for
either experimental or production use. Use at your own risk.

## Functional overview

http-relay is a non-invasive HTTP tunnel which tunnels HTTP requests from web clients such as browsers through a firewall to a web server. It deliberately is transparent to both the browser and the web server, requiring no modifications or changing settings in either.

### Schematic overview of the components involved
      
              Internet             │   │            Intranet
                                   │F W│ 
     ┌─────────┐    ┌─────────┐    │I A│      ┌─────────┐     ┌─────────┐
     │ browser │--->│ public  │<---│R L│<-----│ private │---->│ web     │
     │         │    │ relay   │    │E L│      │ relay   │     │ server  │
     └─────────┘    └─────────┘    │   │      └─────────┘     └─────────┘
                                                      
### Browser
The browser, or for that matter any HTTP client, is outside our control and is unaware of the relay. As far as the browser is concerned, it sends HTTP request to the public relay under a public domain name and thinks it is talking directly to the web server.

### Web server
The web server behind a firewall also isn't aware of the relay, it thinks that the browser is directly talking to it.

### Public relay
The relay consists of two software components which both are capable of networked HTTP: the public relay and the private relay. The public relay runs on a public internet server where the browser and the private relay can access it. It only receives HTTP connections from the browser and the private relay, it never initiates HTTP connections, which means that it needs to run in a "liberal" network environment that allows incoming HTTP connections. The public relay impersonates the web server for the browser, accepts HTTP connections and stores them as request tasks in a FIFO queue. The private relay polls that FIFO queue and writes responses from the web server into the queue which the public relay then sends back to the browser. 

### Private relay
The private relay runs in the intranet behind the firewall; it doesn't accept incoming HTTP connections but it must be able to make outgoing HTTP connections to both the public relay and the web server. The private relay polls the public relay over HTTP for new request tasks and makes HTTP connections to the web server, impersonating the browser. It then sends back the web server responses to the public relay which, in turn, sends them back to the browser.

### Sequence diagram of a browser request

     
     Browser            Public relay     Private relay       Web server
        │               :          :         :                   :
        └>─────────────>┐          :         :                   :
        : 1)HTTP request│          :3) Ask for next task         :
        :               └ . . . . .┐─<─────<─:                   :
        :               :2)Store   │         :                   :
        :               :   as     └>──────>─┐ 5) HTTP request to server
        :               :  task    4) Get    └─>───────────────>─┐
        :               :             task   :                   │
        :               :                    ┌─<───────────────<─┘
        :               :                    │ 6) Server responds:
        :               :          ┌─<─────<─┘                   :
        :               :          │ 7) Post response task       :
        :               : . . . . .┘         :                   :
        :               │          :         :                   :
        ┌─<───────────<─┘          :         :                   :
        │ 8) Send response to browser        :                   :
        
Some important clarifications: the browser is waiting on the HTTP response throughout the entire sequence diagram. There are two sequence lines under
"Public relay" because the private relay actually operates independently; it also is executing a blocking HTTP request and waiting for the public relay
to give it a new task. Once the private relay has received the task, converted it to an HTTP request to the web server and received the response, it
packages the web server response into a response task and writes it back to the public relay. The public relay then extracts the response payload from the
response task and sends it back to the browser.


## TODOs & Known issues

We're briefly addressing known issues; these are not inherent design problems but implementation limitations.

### Not tested
No part was tested under production-like conditions (uptime, infrastructure, load etc).

### Neither secure nor reliable
Relays don't verify request legitimacy, they don't use SSL certificates, they don't validate HTTP requests for correctness and they don't limit requests' frequency or size.

### Not performant
Relays execute old-style synchronous HTTP requests to the web server and between each other, buffer entire requests in memory and store tasks in memory.

### No recovery strategy
Relays don't recover from failures other than timing out requests (which even then will leave stale tasks).

### No useful logging and monitoring
All logging is used for debugging and helping development, we don't expect the implemented logging to be useful in a production setting.

## Production use / workarounds

We strongly advise against using http-relay for production. That having said, the best chances of survival come with running multiple
instances of private and public relays (they are stateless, sort of, so that's ok to do) and using an HTTP load balancer to:

* balance load across multiple public relays
* implement SSL traffic/termination between the browser and the public relay
* implement SSL traffic/termination between the private relay and the public relay
* general network security
* restrict access to the public relay's task queue to intranet IP addresses
* modify HTTP headers, especially Host and Referrer, for web servers expecting traffic from a different domain than the public relay runs under


               Internet             │   │            Intranet
                                    │F W│                                   
      ┌─────────┐    ┌─────────┐    │I A│      ┌─────────┐     ┌─────────┐ 
      │ browser │--->│ HAproxy │<---│R L│<-----│ private │---->│ web     │ 
      │         │    │         │    │E L│      │ relays  │┐    │ server  │ 
      └─────────┘    └─────────┘    │   │      └┬────────┘│┐   └─────────┘ 
                          │         │   │       └┬────────┘│                
                     ┌─────────┐    │   │        └─────────┘ 
                     │ public  │┐   │   │ 
                     │ relays  ││┐  │   │ 
                     └┬────────┘││  │   │ 
                      └┬────────┘│                                 
                       └─────────┘                        
            
