(ns rtc.admin.settings
  (:require
   [rtc.rest.core :as rest]
   [re-frame.core :as rf]))

(defn init-settings [db [_ {:keys [data]}]]
  (-> db
      (assoc :user-id (:id data))
      (assoc-in [:users (:id data)] data)))

(defn current-user [{:keys [users user-id]}]
  (get users user-id))

(defn update-setting [{:keys [user-id] :as db} [_ k v]]
  (assoc-in db [:users user-id k] v))

(defn can-reset-password? [db]
  (let [{:keys [pass pass-confirmation]} (current-user db)]
    (and (seq pass) (seq pass-confirmation) (= pass pass-confirmation))))

(rf/reg-event-db :settings/load init-settings)
(rf/reg-event-db ::update-setting update-setting)
(rf/reg-event-fx
 ::update-settings
 (fn [{:keys [db]} _]
   {::update-settings! (current-user db)}))

(rf/reg-event-db
 ::settings-updated
 (fn [{:keys [user-id] :as db} [_ response]]
   (let [{:keys [errors]} response
         {:keys [reason] :as error} (first errors)
         reason->error-key
         {:pass-confirmation-mismatch :error/password-reset
          :contact-info-invalid :error/settings}]
     (if error
       (assoc-in db [:errors (reason->error-key reason)] error)
       (-> db
           (assoc-in [:errors :error/contact-info] nil)
           (assoc-in [:errors :error/password-reset] nil)
           (assoc-in [:users user-id :pass] "")
           (assoc-in [:users user-id :pass-confirmation] ""))))))

(rf/reg-event-db
 ::settings-error
 (fn [db [_ response]]
   (prn response)
   db))

(rf/reg-sub ::current-user current-user)
(rf/reg-sub ::can-reset-password? can-reset-password?)

(rf/reg-fx
 ::update-settings!
 (fn [payload]
   (rest/post! "/api/v1/admin/settings"
               {:transit-params payload}
               ::settings-updated
               ::settings-error)))

(comment
  @(rf/subscribe [::current-user])
  @(rf/subscribe [:error-message :error/password-reset]))

(defn- setting-field [{:keys [setting type label]}]
  (let [type (or type :text)
        settings @(rf/subscribe [::current-user])]
    [:div.flex-field
     [:label.field-label {:for (name setting)} label]
     [:div.field
      (cond
        (contains? #{:text :email :password} type)
        [:input {:type type
                 :id (name setting)
                 :on-change
                 #(rf/dispatch
                   [::update-setting setting (.. % -target -value)])
                 :value (get settings setting)}]
        (= :checkbox type)
        [:input {:type :checkbox
                 :id (name setting)
                 :on-change
                 #(rf/dispatch
                   [::update-setting setting (.. % -target -checked)])
                 :checked (boolean (get settings setting))}])]]))


(defn settings []
  (let [contact-error @(rf/subscribe [:error-message :error/contact-info])
        pass-error @(rf/subscribe [:error-message :error/password-reset])
        can-reset? @(rf/subscribe [::can-reset-password?])]
    [:div.stack
     [:div.stack.spacious
      [:h3 "Contact Info"]
      (when contact-error
        [:p.error-message contact-error])
      [setting-field {:setting :first_name
                      :label "First Name"}]
      [setting-field {:setting :last_name
                      :label "Last Name"}]
      [setting-field {:setting :email
                      :type :email
                      :label "Email"}]
      [setting-field {:setting :phone
                      :label "Phone"}]
      [setting-field {:setting :is_provider
                      :type :checkbox
                      :label "I am a provider"}]
      [:div
       [:button {:on-click #(rf/dispatch [::update-settings])}
        "Update Details"]]]

     [:div.stack.spacious
      [:h3 "Reset Password"]
      (when pass-error
        [:p.error-message pass-error])
      [setting-field {:setting :pass
                      :type :password
                      :label "New password"}]
      [setting-field {:setting :pass-confirmation
                      :type :password
                      :label "Confirm new password"}]
      [:button {:on-click #(rf/dispatch [::update-settings])
                :disabled (not can-reset?)}
       "Update Password"]]]))