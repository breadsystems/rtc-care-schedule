(ns rtc.rest.core
  (:require
   [clojure.data.json :as json]
   [cognitect.transit :as transit]
   [rtc.admin.schedule :as schedule]
   [rtc.auth.core :as auth]
   [rtc.appointments.core :as appt]
   [rtc.util :as util])
  (:import
    [java.io ByteArrayOutputStream]
    [java.lang Throwable]
    [java.sql Timestamp]
    [java.util Date]
    [java.text SimpleDateFormat]))


(def ^:private default-uid
  (when (= "1" (System/getenv "DEV_DISABLE_AUTH")) 1))

(defn- req->uid [req]
  (get-in req [:session :identity :id] default-uid))

(defn- ->transit [body]
  (let [out (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer body)
    (.toString out)))


(defn- ->json-value [_ v]
  (let [fmt (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")]
    (cond
      (= Date (type v)) (.format fmt v)
      (= Timestamp (type v)) (.format fmt v)
      :else v)))

(defn- ->json [x]
  (json/write-str x :value-fn ->json-value))

(defn- rest-handler [f]
  (fn [req]
    (let [;; The frontend always consumes application/transit+edn data,
          ;; but application/json is useful for debugging
          json? (boolean (get (:params req) "json"))
          transform (if json? ->json ->transit)
          content-type (if json? "application/json" "application/transit+edn")
          res (f req)]
      (if (:success res)
        {:status  200
         :headers {"Content-Type" content-type}
         :body    (transform {:success true
                              :data    (:data res)})}
        {:status 400
         :headers {"Content-Type" content-type}
         :body    (transform {:success false
                              :errors  (:errors res)})}))))

(defonce ONE-DAY-MS (* 24 60 60 1000))

(defn endpoints [{:keys [mount]}]
  [mount
   ["/windows"
    {:get (rest-handler (fn [{:keys [params]}]
                          (let [;; TODO tighten up this logic for more accurate availability
                                ;; Look for availabilities starting this time five days from now
                                from (+ (inst-ms (util/midnight-this-morning)) (* 5 ONE-DAY-MS))
                                to (+ from (* 28 ONE-DAY-MS))
                                state (get params "state")]
                            {:success true
                             :data    (appt/appt-req->windows {:from from
                                                               :to to
                                                               :state state})})))}]
   ["/appointment"
    {:post (fn [req]
             {:status 200
              :headers {"Content-Type" "application/transit+edn"}
              :body (->transit {:success true
                                :appointment (:params req)})})}]
   ["/schedule"
    ;; TODO AUTHORIZE REQUEST!!
    {:get (rest-handler (fn [req]
                          (try
                            {:success true
                             :data    (merge {:user {:id 1}}
                                             (schedule/schedule req))}
                            (catch Throwable e
                              {:success false
                               :errors [{:message "Unexpected error"}]}))))}]])

(comment

  ;;
  )