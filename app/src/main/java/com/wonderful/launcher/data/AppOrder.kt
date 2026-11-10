package com.wonderful.launcher.data

/**
 * アプリの並び替え・起動回数の計算（画面に依存しない部分）
 */
object AppOrder {

    /** list の from 番目を to 番目へ動かした新しいリスト */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        return m
    }

    /**
     * スフィアのアプリを並べ替えた結果を保存用の並びにする。
     * [visible] は画面で並べ替えた順（インストール済み・非表示でないもの）、
     * [stored] は保存されている全部の並び。画面に出ていないもの（アンインストール中など）は後ろに残す。
     */
    fun mergeSphereOrder(visible: List<String>, stored: List<String>): List<String> {
        val seen = visible.toHashSet()
        return (visible + stored.filter { it !in seen }).distinct()
    }

    /** 今日の起動回数に 1 足す（日付が変わっていたら数え直す） */
    fun bumpDaily(counts: Map<String, Int>, countedDay: Long, today: Long, key: String): Map<String, Int> {
        val base = if (countedDay == today) counts else emptyMap()
        return base + (key to ((base[key] ?: 0) + 1))
    }

    /** 今日の起動回数（日付が変わっていたら空） */
    fun todayCounts(counts: Map<String, Int>, countedDay: Long, today: Long): Map<String, Int> =
        if (countedDay == today) counts else emptyMap()

    /** 回数の多い順。同じ回数なら最近使った方を先に */
    fun byCount(counts: Map<String, Int>, lastUsed: Map<String, Long>): List<Pair<String, Int>> =
        counts.filterValues { it > 0 }.toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenByDescending { lastUsed[it.first] ?: 0L })
}
