(set-env!
  :source-paths #{"src"}
  :dependencies '[[compojure "1.3.4"]])

(task-options!
  pom {:project 'api-certs
       :version "0.1.0"}
  aot {:namespace '#{io.vexor.apps.api-certs.core}}
  jar {:manifest {"Foo" "bar"}})

(deftask build
  "Build my project."
  []
  (comp (aot) (pom) (jar)))
