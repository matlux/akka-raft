# akka-raft

Implementation of the raft algorithm in Scala and Akka

Done during the West London Hack night on 23 Jan 2015.

Good fun!

## How it works

Raft is an alternative algorithm to Paxos for reaching consensus in a distributed system.

The definitive description can be found in the original [paper](https://ramcloud.stanford.edu/wiki/download/attachments/11370504/raft.pdf), but we found the following description from https://github.com/hashicorp/raft to be a good introduction:

> Raft nodes are always in one of three states: follower, candidate or leader. All nodes initially start out as a follower. In this state, nodes can accept log entries from a leader and cast votes. If no entries are received for some time, nodes self-promote to the candidate state. In the candidate state nodes request votes from their peers. If a candidate receives a quorum of votes, then it is promoted to a leader. The leader must accept new log entries and replicate to all the other followers. In addition, if stale reads are not acceptable, all queries must also be performed on the leader.
  
> Once a cluster has a leader, it is able to accept new log entries. A client can request that a leader append a new log entry, which is an opaque binary blob to Raft. The leader then writes the entry to durable storage and attempts to replicate to a quorum of followers. Once the log entry is considered committed, it can be applied to a finite state machine. The finite state machine is application specific, and is implemented using an interface.
  
> An obvious question relates to the unbounded nature of a replicated log. Raft provides a mechanism by which the current state is snapshotted, and the log is compacted. Because of the FSM abstraction, restoring the state of the FSM must result in the same state as a replay of old logs. This allows Raft to capture the FSM state at a point in time, and then remove all the logs that were used to reach that state. This is performed automatically without user intervention, and prevents unbounded disk usage as well as minimizing time spent replaying logs.
  
> Lastly, there is the issue of updating the peer set when new servers are joining or existing servers are leaving. As long as a quorum of nodes is available, this is not an issue as Raft provides mechanisms to dynamically update the peer set. If a quorum of nodes is unavailable, then this becomes a very challenging issue. For example, suppose there are only 2 peers, A and B. The quorum size is also 2, meaning both nodes must agree to commit a log entry. If either A or B fails, it is now impossible to reach quorum. This means the cluster is unable to add, or remove a node, or commit any additional log entries. This results in unavailability. At this point, manual intervention would be required to remove either A or B, and to restart the remaining node in bootstrap mode.
  
> A Raft cluster of 3 nodes can tolerate a single node failure, while a cluster of 5 can tolerate 2 node failures. The recommended configuration is to either run 3 or 5 raft servers. This maximizes availability without greatly sacrificing performance.
  
> In terms of performance, Raft is comprable to Paxos. Assuming stable leadership, a committing a log entry requires a single round trip to half of the cluster. Thus performance is bound by disk I/O and network latency.

The visualization at https://raftconsensus.github.io/ is also great for seeing the message flow.

## TODO

Due to time, we chose to implement just the leader selection part of the algorithm. Implementing the message log is left as an exercise to the reader.

We also ignore the possibility of nodes joining/leaving, and instead pass a set of peers to all actors at the start.