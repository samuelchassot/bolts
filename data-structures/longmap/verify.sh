# stainless-dotty ./mutablemap/src/main/scala/ch/epfl/chassot/StrictlyOrderedLongListMap.scala ./mutablemap/src/main/scala/ch/epfl/chassot/MutableLongMap.scala --config-file=stainless.conf --compact -Dparallel=4 $1
stainless-dotty StrictlyOrderedLongListMap.scala MutableLongMap.scala --config-file=stainless.conf --compact $1