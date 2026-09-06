# MyBikeTraffic for Karoo ProGuard rules

# Karoo extension entry point and data types are referenced from the manifest
# / extension_info and must keep their names.
-keep class io.github.aryeh95.mybiketraffic.MyBikeTrafficExtension { *; }
-keep class io.github.aryeh95.mybiketraffic.datatypes.** { *; }

# Karoo SDK models are serialized across the process boundary.
-keep class io.hammerhead.karooext.** { *; }

# Room, Glance, DataStore and kotlinx.serialization ship their own consumer
# rules in their AARs, so nothing else is needed here.
