(ns cloutjure.data.hashes
  (:require [clojure.data.codec.base64 :as b64]
            [clojure.string :refer [lower-case]])
  (:import [java.security MessageDigest]))


(defn- get-hash [type data]
  (->> data
       .getBytes
       (.digest (java.security.MessageDigest/getInstance type))
       b64/encode
       String.))


(doseq [name ["SHA-1" "SHA-256" "SHA-384" "SHA-512" 
              "MD2" "MD5"]]
  (eval `(defn ~(symbol (lower-case name)) [data#]
           (get-hash ~name data#))))
