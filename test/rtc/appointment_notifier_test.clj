(ns rtc.appointment-notifier-test
  (:require
    [clojure.test :refer [are deftest is]]
    [kaocha.repl :as k]
    [rtc.notifier.appointments :as appt]))

;;
;; SMS NOTIFICATIONS
;;

(deftest test-appointment-request->sms
  (are
    [sms appt-req]
    (= sms (appt/appointment-request->sms appt-req))

    {:to "+12535551234"
     :message "Thank you for visiting the RTC. We have received your request for an appointment. We will follow up within 48 hours."}
    {:phone "253 555 1234"}

    {:to "+12535551234"
     :message "Thank you for visiting the RTC. We have received your request for an appointment. We will follow up within 48 hours."}
    {:phone "1253 555 1234"}

    {:to "+12535551234"
     :message "Thank you for visiting the RTC. We have received your request for an appointment. We will follow up within 48 hours."}
    {:phone "253-555-1234"}))

(deftest test-appointment-request->rtc-sms
  (are
    [sms appt-req]
    (= sms (with-redefs [rtc.env/env {:request-notification-phone
                                      "2535551234"}]
             (appt/appointment-request->rtc-sms appt-req)))

    {:to "+12535551234"
     :message
     "New appointment request from a careseeker. Please check the inbox for info@ RTC."}
    {:email "this doesn't really matter."}))

(deftest test-appointment->sms

  (are
    [sms appt]
    (= sms (appt/appointment->sms appt))

    {:to "+12535551234"
     :message "Your appointment at 5:30PM PDT / 8:30PM EDT Fri, Jul 9 with Ursula Le Guin is confirmed."}
    {:phone "253 555 1234"
     :provider_first_name "Ursula"
     :provider_last_name "Le Guin"
     :start_time #inst "2021-07-10T00:30:00.000000000-00:00"}

    {:to "+12535551234"
     :message "Your appointment at 5:30PM PDT / 8:30PM EDT Fri, Jul 9 with Ursula Le Guin is confirmed."}
    {:phone "1 253 555 1234"
     :provider_first_name "Ursula"
     :provider_last_name "Le Guin"
     :start_time #inst "2021-07-10T00:30:00.000000000-00:00"}

    {:to "+12535551234"
     :message "Your appointment at 5:30PM PDT / 8:30PM EDT Fri, Jul 9 with Ursula Le Guin is confirmed."}
    {:phone "+1 253 555 1234"
     :provider_first_name "Ursula"
     :provider_last_name "Le Guin"
     :start_time #inst "2021-07-10T00:30:00.000000000-00:00"}

    {:to "+12535551234"
     :message "Your appointment at 4:30PM PST / 7:30PM EST Tue, Mar 9 with Ursula Le Guin is confirmed."}
    {:phone "+12535551234"
     :provider_first_name "Ursula"
     :provider_last_name "Le Guin"
     :start_time #inst "2021-03-10T00:30:00.000000000-00:00"}))

(deftest test-appointment->provider-sms

  (are
    [sms appt]
    (= sms (appt/appointment->provider-sms appt))

    nil {}
    nil {:provider nil}
    nil {:provider {}} ;; no phone
    nil {:start_time nil :provider {:phone "1234567890"}}
    nil {:start_time "xyz" :provider {:phone "1234567890"}}
    nil {:start_time "2021-03-10T00:33:00.000000000-00:00" ;; not an inst
         :provider {:phone "1234567890"}}

    {:to "+12535551234"
     :message "Someone booked an appointment with you at 4:33PM PST / 7:33PM EST Tue, Mar 9."}
    {:provider {:phone "+12535551234"}
     :start_time #inst "2021-03-10T00:33:00.000000000-00:00"}

    {:to "+12535551234"
     :message "Someone booked an appointment with you at 4:30PM PST / 7:30PM EST Tue, Mar 9."}
    {:provider {:phone "+12535551234"}
     :start_time #inst "2021-03-10T00:30:00.000000000-00:00"}

    {:to "+12535550987"
     :message "Someone booked an appointment with you at 4:30PM PST / 7:30PM EST Tue, Mar 9."}
    {:provider {:phone "+1 253 555 0987"}
     :start_time #inst "2021-03-10T00:30:00.000000000-00:00"}
    ))

(deftest test-send-sms?

  (is (true? (appt/send-sms? {:text-ok 1 :phone "1234567890"})))
  (is (false? (appt/send-sms? {:text-ok nil :phone "1234567890"})))
  (is (false? (appt/send-sms? {:phone "1234567890"})))
  (is (false? (appt/send-sms? {:phone ""})))
  (is (false? (appt/send-sms? {:text-ok 1})))
  (is (false? (appt/send-sms? {:text-ok 321})))
  (is (false? (appt/send-sms? {:text-ok false :phone "123467890"}))))

;;
;; EMAIL NOTIFICATIONS
;;

(deftest test-appointment-request->email
  (are
    [email appt-req]
    (= email (appt/appointment-request->email appt-req))

    {:to "careseeker@example.com"
     :to-name nil
     :subject "Your appointment with the Radical Telehealth Collective"
     :message "Thank you for visiting the RTC. We have received your request for an appointment. Honoring your preferences, we will follow up within 48 hours."}
    {:email "careseeker@example.com"}

    {:to "careseeker@example.com"
     :to-name "Shevek"
     :subject "Your appointment with the Radical Telehealth Collective"
     :message "Thank you for visiting the RTC. We have received your request for an appointment. Honoring your preferences, we will follow up within 48 hours."}
    {:email "careseeker@example.com"
     :name "Shevek"}
    ))

(deftest test-appointment-request->email
  (are
    [email appt-req]
    (= email (with-redefs [rtc.env/env {:request-notification-email
                                        "info@rtc.email"}]
               (appt/appointment-request->rtc-email appt-req)))

    {:to "info@rtc.email"
     :to-name "Radical Telehealth Collective"
     :subject "New appointment request from S."
     :message
     (str
       "There has been a new appointment request on radicaltelehealthcollective.org:\n\n"
       "Name: Shevek\n"
       "Pronouns: he/him\n"
       "State: WA\n"
       "Email: careseeker@example.email\n"
       "Phone: 253 555 1234\n"
       "Text OK: Yes\n"
       "Preferred comm. method: phone\n"
       "Needs interpreter for: Amharic\n"
       "Other access needs: Other\n"
       "Description of medical needs: Life is pain\n"
       "Anything else: Nah"
       )}
    {:name "Shevek"
     :pronouns "he/him"
     :state "WA"
     :email "careseeker@example.email"
     :phone "253 555 1234"
     :text-ok 1
     :preferred-communication-method "phone"
     :interpreter-lang "Amharic"
     :other-access-needs "Other"
     :description-of-needs "Life is pain"
     :anything-else "Nah"}

    {:to "info@rtc.email"
     :to-name "Radical Telehealth Collective"
     :subject "New appointment request from U.L.G."
     :message
     (str
       "There has been a new appointment request on radicaltelehealthcollective.org:\n\n"
       "Name: Ursula Le Guin\n"
       "Pronouns: she/her\n"
       "State: WA\n"
       "Email: ursula@earthsea.net\n"
       "Phone: 253 555 9876\n"
       "Text OK: No\n"
       "Preferred comm. method: email\n"
       "Needs interpreter for: No answer\n"
       "Other access needs: Temporary secretary\n"
       "Description of medical needs: Existential boredom\n"
       "Anything else: Lots!"
       )}
    {:name "Ursula Le Guin"
     :pronouns "she/her"
     :state "WA"
     :email "ursula@earthsea.net"
     :phone "253 555 9876"
     :text-ok 0
     :preferred-communication-method "email"
     :interpreter-lang ""
     :other-access-needs "Temporary secretary"
     :description-of-needs "Existential boredom"
     :anything-else "Lots!"}

    {:to "info@rtc.email"
     :to-name "Radical Telehealth Collective"
     :subject "New appointment request from Anon."
     :message
     (str
       "There has been a new appointment request on radicaltelehealthcollective.org:\n\n"
       "Name: Anonymous\n"
       "Pronouns: No answer\n"
       "State: DC\n"
       "Email: ursula@earthsea.net\n"
       "Phone: No answer\n"
       "Text OK: No answer\n"
       "Preferred comm. method: No answer\n"
       "Needs interpreter for: No answer\n"
       "Other access needs: No answer\n"
       "Description of medical needs: Something to ease the pain\n"
       "Anything else: No answer"
       )}
    {:name ""
     :pronouns ""
     :state "DC"
     :email "ursula@earthsea.net"
     :phone ""
     :text-ok nil
     :preferred-communication-method ""
     :interpreter-lang ""
     :other-access-needs ""
     :description-of-needs "Something to ease the pain"
     :anything-else ""}))

(deftest test-appointment->email

  (are
    [email appt]
    (= email (appt/appointment->email appt))

    {:to "careseeker@example.com"
     :to-name nil
     :subject "Your appointment with the Radical Telehealth Collective"
     :message "Your appointment at 5:30PM PDT / 8:30PM EDT Fri, Jul 9 with Ursula Le Guin is confirmed. Thank you for booking your appointment with the Radical Telehealth Collective."}
    {:email "careseeker@example.com"
     :provider_first_name "Ursula"
     :provider_last_name "Le Guin"
     :start_time #inst "2021-07-10T00:30:00.000000000-00:00"}

    ;; With careseeker name
    {:to "careseeker@example.com"
     :to-name "Shevek"
     :subject "Your appointment with the Radical Telehealth Collective"
     :message "Your appointment at 5:30PM PDT / 8:30PM EDT Fri, Jul 9 with Ursula Le Guin is confirmed. Thank you for booking your appointment with the Radical Telehealth Collective."}
    {:email "careseeker@example.com"
     :name "Shevek"
     :provider_first_name "Ursula"
     :provider_last_name "Le Guin"
     :start_time #inst "2021-07-10T00:30:00.000000000-00:00"}

    nil
    {;; no email
     :name "Shevek"
     :provider_first_name "Ursula"
     :provider_last_name "Le Guin"
     :start_time #inst "2021-07-10T00:30:00.000000000-00:00"}

    nil
    {:email "me@example.com"
     :name "Shevek"
     ;; no provider_first_name
     :provider_last_name "Le Guin"
     :start_time #inst "2021-07-10T00:30:00.000000000-00:00"}

    nil
    {:email "me@example.com"
     :name "Shevek"
     :provider_first_name "Ursula"
     ;; no provider_last_name
     :start_time #inst "2021-07-10T00:30:00.000000000-00:00"}

    nil
    {:email "me@example.com"
     :name "Shevek"
     :provider_first_name "Ursula"
     :provider_last_name "Le Guin"
     ;; no start_time
     }

    nil
    {:email "me@example.com"
     :name "Shevek"
     :provider_first_name "Ursula"
     :provider_last_name "Le Guin"
     ;; not an inst:
     :start_time "2021-07-10T00:30:00.000000000-00:00"}
    ))

(deftest test-appointment->provider-email

  (are
    [email appt]
    (= email (appt/appointment->provider-email appt))

    nil {}
    nil {:provider nil}
    nil {:provider {}} ;; no email
    nil {:start_time nil :provider {:email "rtc@example.org"}}
    nil {:start_time "xyz" :provider {:email "rtc@example.org"}}
    nil {:start_time "2021-03-10T00:33:00.000000000-00:00" ;; not an inst
         :provider {:email "rtc@example.org"}}

    {:to "rtc@example.org"
     :subject "New RTC Appointment"
     :message "Someone booked an appointment with you at 4:33PM PST / 7:33PM EST Tue, Mar 9. Go to https://www.radicaltelehealthcollective.org/comrades for details."}
    {:provider {:email "rtc@example.org"}
     :start_time #inst "2021-03-10T00:33:00.000000000-00:00"}

    {:to "rtc@example.org"
     :subject "New RTC Appointment"
     :message "Someone booked an appointment with you at 4:30PM PST / 7:30PM EST Tue, Mar 9. Go to https://www.radicaltelehealthcollective.org/comrades for details."}
    {:provider {:email "rtc@example.org"}
     :start_time #inst "2021-03-10T00:30:00.000000000-00:00"}

    {:to "rtc@example.org"
     :subject "New RTC Appointment"
     :message "Someone booked an appointment with you at 4:30PM PST / 7:30PM EST Tue, Mar 9. Go to https://www.radicaltelehealthcollective.org/comrades for details."}
    {:provider {:email "rtc@example.org"}
     :start_time #inst "2021-03-10T00:30:00.000000000-00:00"}
    ))

(deftest test-send-email?

  (is (true? (appt/send-email? {:email "rtc@example.com"})))
  (is (false? (appt/send-email? {})))
  (is (false? (appt/send-email? nil)))
  (is (false? (appt/send-email? {:email ""}))))

(comment
  (k/run))