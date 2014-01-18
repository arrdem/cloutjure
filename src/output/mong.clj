(ns cloutjure.output.mong
  (:require [somnium.congomongo       :as m]))

(defn insert!
  [where what]
  (m/insert! where what))
