(ns cloutjure.output.neo
  (:require [clojurewerkz.neocons.rest :as nr]
            [clojurewerkz.neocons.rest.nodes :as nn]))

(def connection (atom false))

(defn insert!
  [where what]
  (when-not @connection
    (swap! connection (fn [_]
                        (nr/connect! "http://localhost:7474/db/data/")
                        true)))
  ;; FIXME: Use nn/create instead
  (nn/create (assoc what :source where)))
