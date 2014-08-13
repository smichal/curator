(ns curator.path
  (:import [org.apache.curator.utils ZKPaths]))

(defn get-zookeeper
  "Get zookeeper."
  [client]
  (.getZooKeeper (.getZookeeperClient client)))

(defn rmr
  "Recursively deletes children of a node.
  client curator framework
  path path of the node to delete
  bool-delete-self flag that indicates that the node should also get deleted "
  [client path bool-delete-self]
  (ZKPaths/deleteChildren (get-zookeeper client) path bool-delete-self))

(defn mkdirs
  "Make sure all the nodes in the path are created. NOTE: Unlike File.mkdirs(), Zookeeper doesn't distinguish
  between directories and files. So, every node in the path is created. The data for each node is an empty blob."
  ([client path]
     (ZKPaths/mkdirs (get-zookeeper client) path))
  ([client path make-last-node]
     (ZKPaths/mkdirs (get-zookeeper client) path make-last-node))
  ([client path make-last-node acl-provider]
     (ZKPaths/mkdirs (get-zookeeper client) path make-last-node acl-provider)))
