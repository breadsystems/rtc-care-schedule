(ns rtc.style.core
  (:require
   [garden.color :refer [rgb]]
   [garden.def :refer [defcssfn]]
   [garden.selectors :refer [attr &]]))


(defcssfn url)
(defcssfn calc)

(def pink (rgb 255 26 79))
(def muted-pink (rgb 185 90 115))
(def off-white (rgb 255 230 250))
(def purple "#b9a1fb")
(def grey (rgb 100 100 100))

(def purple-border (str "2px solid " purple))


(def base
  [])

(def typography
  [[:h1 {:color pink
         :text-transform :uppercase}]
   [:.help {:color grey
            :font-style :italic}]])

(def forms
  [[:.field {:margin "0.7em 0"}]
   [:.radio-option {:margin "0 2em 0 0"}]
   [:input [(& (attr :type=text))
            (& (attr :type=email))
            (& (attr :type=password))
            {:min-width "25rem"
             :padding "0.7em"
             :border-radius "0.3em"
             :border purple-border}]]
   [:button {:padding "0.7em 1.3em"
             :border :none
             :cursor :pointer
             :font-weight 700
             :text-transform :uppercase
             :color :white
             :background pink}
    [:&.next {:text-align :right}]
    [:&:disabled {:background-color muted-pink
                  :color off-white
                  :cursor :not-allowed}]]
   [:select {:min-width "25rem"
             :border purple-border
             :border-radius "0.3em"
             :background-color :lavender
             :background-image (url "/img/caret-down.svg")
             :background-position "right 10px bottom 12px"
             :background-repeat :no-repeat
             :padding "0.7em"
             :-moz-appearance :none
             :-webkit-appearance :none}
    [:&:ms-expand {:display :none}]]])