package org.konaframework.support.console

/** Stdin closed (EOF) while a prompt was waiting; installers map this to a usage error. */
class PromptEofException(message: String = "Cannot read stdin.") : RuntimeException(message)

/** Ctrl+C in a raw TTY prompt after the terminal has been restored. */
class PromptInterruptedException(message: String = "Cancelled.") : RuntimeException(message)
