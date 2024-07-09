name := "MutableMaps"
version := "0.1.0-SNAPSHOT"
scalaVersion :="3.3.3" 

run / fork := true

Compile / mainClass := Some("ch.epfl.chassot.Main")

stainlessEnabled := false

enablePlugins(StainlessPlugin, JmhPlugin)

enablePlugins(ScalaNativePlugin)


import scala.scalanative.build._

nativeConfig ~= {
  _.withLTO(LTO.none)
    .withMode(Mode.releaseFull)
    .withGC(GC.immix)
}
