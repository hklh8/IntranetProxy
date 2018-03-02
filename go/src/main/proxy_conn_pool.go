package main

import (
	"log"
	"sync"
)

type Pooler interface {
	Create(pool *ConnHandlerPool) (*ConnHandler, error)
	Remove(conn *ConnHandler)
	IsActive(conn *ConnHandler) bool
}

type ConnHandlerPool struct {
	Size   int
	Pooler Pooler
	mu     sync.Mutex
	conns  []*ConnHandler
}

func (connPool *ConnHandlerPool) Init() {
	connPool.conns = make([]*ConnHandler, 0, connPool.Size)
	log.Printf("初始化连接池, len %d, cap %d", len(connPool.conns), cap(connPool.conns))
}

func (connPool *ConnHandlerPool) Get() (*ConnHandler, error) {
	for {
		if len(connPool.conns) == 0 {
			conn, err := connPool.Pooler.Create(connPool)
			log.Println("创建连接: ", conn, err)
			if err != nil {
				return nil, err
			}

			return conn, nil
		} else {
			conn, err := connPool.getConn()
			if conn != nil {
				return conn, err
			}
		}
	}
}

func (connPool *ConnHandlerPool) getConn() (*ConnHandler, error) {
	connPool.mu.Lock()
	defer connPool.mu.Unlock()
	if len(connPool.conns) == 0 {
		return nil, nil
	}
	conn := connPool.conns[len(connPool.conns)-1]
	connPool.conns = connPool.conns[:len(connPool.conns)-1]
	if connPool.Pooler.IsActive(conn) {
		log.Println("从连接池获取连接: ", conn)
		return conn, nil
	} else {
		return nil, nil
	}
}

func (connPool *ConnHandlerPool) Return(conn *ConnHandler) {
	connPool.mu.Lock()
	defer connPool.mu.Unlock()
	if len(connPool.conns) >= connPool.Size {
		log.Println("连接池已满,移除连接: ", conn)
		connPool.Pooler.Remove(conn)
	} else {
		connPool.conns = connPool.conns[:len(connPool.conns)+1]
		connPool.conns[len(connPool.conns)-1] = conn
		log.Println("返回连接:", conn, ", 连接池大小为 ", len(connPool.conns))
	}
}
