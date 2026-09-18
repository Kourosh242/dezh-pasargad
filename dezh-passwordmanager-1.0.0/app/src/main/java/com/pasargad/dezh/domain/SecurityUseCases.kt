package com.pasargad.dezh.domain

/** Thin orchestration layer above [VaultSecurityRepository] (architecture: Presentation → UseCase → Repository). */

class SetupMasterPasswordUseCase(private val repository: VaultSecurityRepository) {
    suspend operator fun invoke(masterPassword: CharArray) = repository.setup(masterPassword)
}

class UnlockVaultUseCase(private val repository: VaultSecurityRepository) {
    suspend operator fun invoke(masterPassword: CharArray) = repository.unlock(masterPassword)
}

class LockVaultUseCase(private val repository: VaultSecurityRepository) {
    operator fun invoke() = repository.lock()
}
