(ns curator.core
  (:require [potemkin.namespaces :refer [import-vars]]))

(import-vars [curator.framework framework]
             [curator.discovery discovery services provider cache
              note-error instance instances register unregister update]
             [curator.leader leader-selector interrupt-leadership
              leader? leader participants]
             [curator.path rmr mkdirs])
