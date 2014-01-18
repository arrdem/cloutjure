(ns cloutjure.common
  (:require [somnium.congomongo :as m]))

(defn parse-shell-config
  "Reads a string of the form <user>(:<pasword>)@<host>/<database>
  from the environment variable MONGO_CONFIG and generates a map with
  the keys (:user, :pass, :host, :db)."

  ([]
     (if-let [val (-> (System/getenv)
                      (get "MONGO_CONFIG"))]
       (parse-shell-config val)))

  ([val]
     (->> val
          (re-find #"(([^:@]+)(:([^@]+))?@)?([^/:]+)(:(\d+))?/(\w+)")
          ((fn [[_ _ uname _ pass host _ port db]]
             {:user uname
              :pass pass
              :host host
              :port (or port 27017)
              :db db})))))


(defn make-connection 
  "Accepts a configuration map as parsed by parse-shell-config and
  generates a Mongodb connection with the configuations specified by
  the config map."

  [{:keys [user pass host port db]}]
  (let [conn (m/make-connection db :host host :port port)]
    (when (and user pass)
      (m/authenticate conn user pass))
    conn))


(defn init-db!
  "Attempts to automagically open a connection to the cloutjure
  database by parsing the standard database configuration environment
  variable and opening a Mongo connection as defined. Note that this
  function uses congomongo/set-connection! so the connection created
  here is forced globally for better or worse."

  []
  (-> (System/getenv)
      (get "MONGO_CONFIG")
      (parse-shell-config)
      (make-connection)
      (m/set-connection!)))
