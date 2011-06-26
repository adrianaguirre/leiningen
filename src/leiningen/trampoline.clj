(ns leiningen.trampoline
  (:refer-clojure :exclude [trampoline])
  (:use [leiningen.core :only [apply-task task-not-found abort]]
        [leiningen.compile :only [get-input-args get-readable-form
                                  prep eval-in-project]]
        [leiningen.classpath :only [get-classpath-string]])
  (:require [clojure.string :as string]))

(defn escape [form-string]
  (format "\"%s\"" (.replaceAll form-string "\"" "\\\\\"")))

(defn command-string [project java-cmd jvm-opts [form init]]
  (string/join " " ["exec" java-cmd "-cp" (get-classpath-string project)
                    "clojure.main" "-e"
                    (escape (get-readable-form nil project form init))]))

(defn write-trampoline [command]
  (spit (System/getProperty "leiningen.trampoline-file") command))

(defn trampoline
  "Run a task without nesting the project's JVM inside Leiningen's.

Calculates what needs to run in the project's process for the provided
task and runs it after Leiningen's own JVM process has exited rather
than as a subprocess of Leiningen's project.

Use this to save memory or to work around things like Ant's stdin
issues. Not compatible with chaining.

ALPHA: subject to change without warning."
  [project task-name & args]
  (let [java-cmd (format "%s/bin/java" (System/getProperty "java.home"))
        jvm-opts (get-input-args)
        jvm-opts (if (:debug project)
                   (conj jvm-opts "-Dclojure.debug=true")
                   jvm-opts)
        eval-args (atom nil)]
    (when (:eval-in-leiningen project)
      (println "Warning: trampoline has no effect with :eval-in-leiningen."))
    (binding [eval-in-project (fn [project form & [_ _ init]]
                                (prep project true)
                                (reset! eval-args [form init]) 0)]
      (apply-task task-name project args task-not-found))
    (if @eval-args
      (write-trampoline (command-string project java-cmd jvm-opts @eval-args))
      (abort task-name "is not trampolineable."))))
