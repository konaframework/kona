package org.konaframework.cadence.document

/** Test stand-in for the M27 document base. The tables module must not query it as SQL. */
abstract class Document<T : Document<T>>

class Note : Document<Note>()
