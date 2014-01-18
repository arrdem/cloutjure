(ns cloutjure.data.messages
  (:require [cloutjure.data.hashes :refer [sha-1 md5]]
            [somnium.congomongo :as m]
            [clj-time.core :refer [DateTimeProtocol]])
  (:import [java.security MessageDigest]))

;; ## Messages
;;
;; Messages are maps
;;
;; ```Clojure
;; {
;;  :author author,
;;  :date   date,
;;  :source source,
;;  :hash   hash
;; }
;; ```
;;
;;  - `author` - a string, being the as presented name of the author
;;  - `date`   - the date on which the message was sent as a clj-time
;;               map rather than a raw DateTime object
;;  - `source` - the channel or medium which it was sent to `hash`
;;               being the sha1 of the message truncated to 8
;;               characters
;;
;; Note: The `hash` field was introduced so that messages could be
;; de-duplicated while retaining support for multiple messages sent
;; within some unknown timeframe. The idea being that as IRC logs are
;; typically datestamped to the minute adding data to an existing
;; datastore would not be possible without creating duplicates of
;; existing entries because the timestamp field cannot be used to
;; deduplicate reliably. With a `hash` field however one can
;; confidently deduplicate by `hash` and `date`.
;;
;; Messages are stored in their own message table, the expected access
;; pattern to which is sadly scan counts. The insertion operation is
;; probably going to be `(message/maybe-log! <message>)`, which will
;; deduplicate by message hash rather than blindly executing an
;; insertion statement.


(def ^:dynamic *hash-fn* sha-1)

(def ^:dynamic *collection* :messages)


(defn message?
  "Checks the argument structure against the type structure definition
  of a Message."

  [maybe-mesage]
  (and (map? maybe-mesage)
       (every? (partial contains? maybe-mesage)
               [:author :date :source :hash])
       (string? (:author maybe-mesage))
       (string? (:source maybe-mesage))
       (string? (:hash   maybe-mesage))
       (map? (:date maybe-mesage))))


(defn hash-text
  "Computes the hash of a message text for quick comparison. The hash
  function is defined to be the first eight characters of the SHA1 of
  the message body. This hash is pretty poor, but it doesn't have to
  be especially good since it serves only as a quick de-duplication
  check field in combination with the message date.

  Note that the hashing operation is referential through the ^:dynamic
  *hash-fn* symbol, so it is quite possible to configure the hashing
  algorithtm used to compute the message digests."

  [text]
  (*hash-fn* text))


(defn log! 
  "Inserts a message into the logstore after verifying that it is a
  valid message and computing its hash just to make sure."

  [maybe-message]
  (when (message? maybe-message)
    (if (m/fetch *collection* {:hash (:hash maybe-message)})
      (m/update! *collection* maybe-message)
      (m/insert! *collection* maybe-message))))
