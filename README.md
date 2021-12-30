# 简介
该项目架构设计文章：https://my.oschina.net/1Gk2fdm43/blog/5312538

该项目主要为海量日志（秒级GB级）的搜集、传输、存储而设计的全套方案。区别于传统的如ELK系列套件，该项目主要是为了解决超大量级的日志（出入参、链路跟踪日志）从搜集到最后检索查询的中途产生的高昂硬件成本、性能低下等问题。

核心点在于 **性能、成本** 。

较ELK系列方案（filebeat、mq传输、es存储等常见方案），该框架拥有10倍以上的性能提升，和70%以上的磁盘节省。这意味着，在日志这个功能块上，使用相同的硬件配置，原本只能传输、存储一秒100M的日志，采用该方案，一秒可以处理1GB的日志，且将全链路因日志占用的磁盘空间下降70%。
# 背景
京东App作为一个巨大量级的请求入口，涉及了诸多系统，为了保证系统的健壮性、和请求溯源，以及出现问题后的问题排查，通常我们保存了用户请求从出入参、系统中途关键节点日志（info、error）、链路日志等，并且会将日志保存一段时间。

