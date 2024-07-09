stainless-dotty \
./src/main/scala/ch/epfl/chassot/MutableLongMapWithoutSpec.scala \
./src/main/scala/ch/epfl/chassot/Main.scala \
./src/main/scala/ch/epfl/chassot/OptimisedChecks.scala \
--config-file=stainless.conf \
-Dparallel=4 \
--genc=true \
$1
