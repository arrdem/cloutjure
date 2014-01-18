(ns cloutjure.input.n01se-irc
  (:require (cloutjure.data [messages :as messages]
                            [links    :as links]
                            [hashes   :refer [sha-256]])
            [cloutjure.common         :as common :refer [init-db!]]
            [pl.danieljanus.tagsoup   :as tagsoup]
            [somnium.congomongo       :as m]
            (clj-time       [core     :as t]
                            [format   :as t.f])
            [swiss-arrows.core :refer :all])
  (:gen-class))


(def url-formatter (t.f/formatter "YYYY-MM-dd"))

(defn date->url
  "Formats a clj-time date into a logfile path for
  http://clojure-log.n01se.net/date/*."
  [date]
  (format "http://clojure-log.n01se.net/date/%s.html"
          (t.f/unparse url-formatter date)))


(def hour-minute-formatter (t.f/formatters :hour-minute))

(defn p->message
  "Converts a [:p {} [:a {} <date-in-minutes-and-seconds>] 
                     [:b {} <user>]
                     & message]

   into the appropriate messages/message? compliant
   datastructure. Note that this implementation _does_ preserve the
   exact message text, which must be explicitly stripped before it is
   inserted to the database never to be destroyed."
  [name-atom year-month-day-date [_p _m0 [_a _m1 date] & tail]]

  (if (vector? (first tail))
    (let [[_b _m uname] (first tail)
          user (second (re-find #"(\w*?):.*" uname))]
      (when (or (not (re-find #"\\*" uname))
                user)
        (reset! name-atom user))))
      
  (let [message (if (vector? (first tail)) (rest tail) tail)
        message (apply str (interpose " " message))]
    {:message message
     :author  @name-atom
     :date    (let [{:keys [hours minutes]} 
                    (->> date
                         (t.f/parse hour-minute-formatter)
                         (t.f/instant->map))]
                (t.f/instant->map
                 (t/plus year-month-day-date
                         (t/hours   hours)
                         (t/minutes minutes))))
     :hash    (sha-256 message)}))


(def clj-epoch (t/date-time 2008 2 1))

(defn process-days-log! 
  "Takes a date and attempts to parse the day's logs, writing to the
  datastore as appropriate."

  [day]
  (try
    (let [tree (tagsoup/parse (date->url day))
          name-atom (atom "")]
      (doseq [message (-<> tree
                           (get-in <> [3 3 8])
                           (drop 2 <>)
                           (filter #(= :p (first %1)) <>))]
        (let [message (p->message name-atom day message)]
          (try
            (assert (not (= (:author message) " ")))
            (m/insert! :messages message)
            (catch Exception e (println day " | "  e " | " (pr-str message)))))))
    (println day " | " nil " | " nil)
    (catch Exception e (println day " | "  e " | " nil))))


(defn -main
  "The entry point of this codebase, essentially a simple crawler
  script which browses http://clojure-log.n01se.net/date/* and uses
  tagsoup to parse the html into a series of messages which are loaded
  into the cloutjure datastore via cloutjure.data as one would expect.

  This function takes no options and is desinged to be run as a single
  threaded script with no paralellism besides any which Clojure may
  sneak in behind my back."

  []
  (common/init-db!)
  (let [interval  (t/interval clj-epoch (t/now))]
    (doseq [day-offset (range (t/in-days interval))]
      (let [date (->> day-offset
                      (t/days)
                      (t/plus clj-epoch))]
        (process-days-log! date)))))

