package org.prairieserver.prairie.util

import kotlinx.coroutines.CoroutineDispatcher

expect val ioDispatcher: CoroutineDispatcher
expect val mainDispatcher: CoroutineDispatcher
