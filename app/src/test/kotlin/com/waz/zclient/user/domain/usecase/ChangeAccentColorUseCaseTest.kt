package com.waz.zclient.user.domain.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.user.data.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class ChangeAccentColorUseCaseTest : UnitTest() {

    private lateinit var changeAccentColorUseCase: ChangeAccentColorUseCase

    @Mock
    private lateinit var userRepository: UsersRepository

    @Mock
    private lateinit var changeAccentColorParams : ChangeAccentColorParams

    @Before
    fun setup() {
        changeAccentColorUseCase = ChangeAccentColorUseCase(userRepository)
    }

    @Test
    fun `Given update accent color use case is executed, then the repository should update accent color`() = runBlockingTest {
        `when`(changeAccentColorParams.newAccentColorId).thenReturn(TEST_ACCENT_COLOR)

        changeAccentColorUseCase.run(changeAccentColorParams)

        verify(userRepository).changeAccentColor(eq(TEST_ACCENT_COLOR))
    }

    companion object {
        private const val TEST_ACCENT_COLOR = 2
    }
}
