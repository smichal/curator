(ns ^{:doc "Namespace for service discovery"} curator.discovery
    (:require [clojure.edn :as edn]
              [curator.framework :refer (time-units)]
              [clojure.string :as s])
    (:import [org.apache.curator.x.discovery ServiceDiscovery ServiceDiscoveryBuilder ServiceInstance ServiceType UriSpec ProviderStrategy DownInstancePolicy ServiceProvider ServiceCache]
             [org.apache.curator.x.discovery.details InstanceSerializer JsonInstanceSerializer InstanceProvider]
             [org.apache.curator.x.discovery.strategies RandomStrategy RoundRobinStrategy StickyStrategy]
             [java.io ByteArrayInputStream InputStreamReader PushbackReader]))

(defmacro dotonn [x & forms]
  (let [gx (gensym)]
    `(let [~gx ~x]
       ~@(map (fn [f]
                (if (seq? f)
                  `(when ~@(next f)
                     (~(first f) ~gx ~@(next f)))
                  `(~f ~gx)))
              forms)
       ~gx)))

(defn uri-spec*
  "Creates a templated UriSpec from a string format.
  Example: e.g. \"{scheme}://foo.com:{port}\"
  Substitutions can include: scheme, name, id, address,
  port, ssl-port, registration-time-utc, service-type"
  [s]
  (UriSpec. s))

(defn new-instance
  "Create a new service instance.
  name: my-service
  uri-spec: \"{scheme}://foo.com:{port}\"
  port: 1234
  payload is serialized using json, only supports strings for now"
  [name uri-spec & {:keys [port id address ssl-port service-type payload]}]
  {:pre [(or (nil? payload) (string? payload))]}
  (let [service-types {:dynamic   ServiceType/DYNAMIC
                       :static    ServiceType/STATIC
                       :permanent ServiceType/PERMANENT}
        port (if port port (Integer/parseInt (last (s/split uri-spec #":"))))]
    (-> (dotonn (ServiceInstance/builder)
                (.payload payload)
                (.name name)
                (.id id)
                (.address address)
                (.port port)
                (.sslPort ssl-port)
                (.uriSpec (uri-spec* uri-spec))
                (.serviceType (service-types service-type)))
        (.build))))

(defn uri [service-instance]
  (.buildUriSpec service-instance))

(defn json-serializer []
  (JsonInstanceSerializer. String))

(defn discovery
  [curator-framework & {:keys [base-path serializer payload-class instance]
                        :or   {base-path     "/foo"
                               payload-class String
                               serializer    (json-serializer)}}]
  {:pre [(.startsWith base-path "/")]}
  (-> (dotonn (ServiceDiscoveryBuilder/builder payload-class)
              (.client curator-framework)
              (.basePath base-path)
              (.serializer (json-serializer))
              (when instance (.thisInstance instance)))
      (.build)))

(defn services
  "Returns the names of the services registered."
  [service-discovery]
  (.queryForNames service-discovery))

(defn random-strategy
  []
  (RandomStrategy. ))

(defn round-robin-strategy
  []
  (RoundRobinStrategy. ))

(defn sticky-strategy
  [^ProviderStrategy strategy]
  (StickyStrategy. strategy))

(defn down-instance-policy
  ([] (down-instance-policy 30 :seconds 2))
  ([timeout timeout-unit error-threshold]
     {:pre [(some time-units [timeout-unit])]}
     (DownInstancePolicy. timeout (time-units timeout-unit) error-threshold)))

(defn provider
  "Creates a service provider for a named service s."
  [service-discovery s & {:keys [strategy down-instance-policy]
                          :or   {strategy             (random-strategy)
                                 down-instance-policy (down-instance-policy)}}]
  (-> (doto (.serviceProviderBuilder service-discovery)
        (.serviceName s)
        (.downInstancePolicy down-instance-policy)
        (.providerStrategy strategy))
      (.build)))

(defn service-cache
  "Creates a service cache (rather than reading ZooKeeper each time) for
  the service named s"
  [service-discovery s]
  (-> (.serviceCacheBuilder service-discovery)
      ( .name s)
      (.build)))

(defn note-error
  "Clients should use this to indicate a problem when trying to
  connect to a service instance. The instance may be marked as down
  depending on the service provider's down instance policy."
  [^ServiceProvider service-provider ^ServiceInstance instance]
  (.noteError service-provider instance))

(defmulti instances (fn [x & args] (.getClass x)))
(defmethod instances ServiceDiscovery [sd s] (.queryForInstances sd s))
(defmethod instances ServiceCache [sc] (.getInstances sc))

(defmulti instance (fn [x & args] (.getClass x)))
(defmethod instance ServiceProvider [provider] (.getInstance provider))
(defmethod instance ServiceCache [cache ^ProviderStrategy strategy] (.getInstance strategy cache))

(defn register
  "Register/re-register a service"
  [discovery service]
  (.registerService discovery service))

(defn unregister
  "Unregister/remove a service"
  [discovery service]
  (.unregisterService discovery service))
