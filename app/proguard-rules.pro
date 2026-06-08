# SPDX-FileCopyrightText: 2026 ursa.nz <code@ursa.nz>
# SPDX-License-Identifier: GPL-3.0-or-later

# extJWNL instantiates its dictionary and morphology implementations by name, via reflection, from
# the XML configuration bundled in its jar (the FileBackedDictionary, the Princeton file readers and
# element factory, the morphological processors). R8 must therefore keep the whole package and not
# warn about the optional integrations it references but the app does not use.
-keep class net.sf.extjwnl.** { *; }
-dontwarn net.sf.extjwnl.**

# extJWNL pulls in a concurrent LRU cache and slf4j; both reference compile-only JSR-305 annotations
# and slf4j's optional static binder, none of which are present or needed at runtime.
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn javax.annotation.concurrent.Immutable
-dontwarn javax.annotation.concurrent.NotThreadSafe
-dontwarn javax.annotation.concurrent.ThreadSafe
-dontwarn org.slf4j.impl.StaticLoggerBinder
