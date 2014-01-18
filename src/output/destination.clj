b(ns cloutjure.output.destination
  (:require [cloutjure.output.mong :as m]
            [cloutjure.output.neo :as n]))

(defn insert!
  "Change n to m to save to mongo db instead
This is cheeseball. Hacking at its finest!"
  [where what]
  (n/insert! where what))
