package com.water.widget

/**
 * 将账户划分为可并发运行的执行通道。
 * 任意两个属于同一账户、共享 uid、主 Token 或 App Token 的记录会被放入同一通道，通道内按原顺序串行执行。
 */
data class AccountExecutionLane(val accounts: List<Account>)

object AccountExecutionPlanner {
    fun plan(accounts: List<Account>): List<AccountExecutionLane> {
        if (accounts.isEmpty()) return emptyList()

        val parent = IntArray(accounts.size) { it }
        fun find(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }
        fun union(first: Int, second: Int) {
            val firstRoot = find(first)
            val secondRoot = find(second)
            if (firstRoot != secondRoot) parent[secondRoot] = firstRoot
        }

        val ownerByToken = mutableMapOf<String, Int>()
        accounts.forEachIndexed { index, account ->
            accountTokens(account).forEach { token ->
                val owner = ownerByToken.putIfAbsent(token, index)
                if (owner != null) union(owner, index)
            }
        }

        val lanes = linkedMapOf<Int, MutableList<Account>>()
        accounts.forEachIndexed { index, account ->
            lanes.getOrPut(find(index)) { mutableListOf() } += account
        }
        return lanes.values.map(::AccountExecutionLane)
    }

    private fun accountTokens(account: Account): List<String> =
        buildList {
            account.phone?.trim()?.takeIf { it.isNotEmpty() }?.let { add("account:$it") }
            account.uid?.trim()?.takeIf { it.isNotEmpty() }?.let { add("uid:$it") }
            account.token?.trim()?.takeIf { it.isNotEmpty() }?.let { add("token:$it") }
            account.appToken?.trim()?.takeIf { it.isNotEmpty() }?.let { add("token:$it") }
        }.distinct()
}
