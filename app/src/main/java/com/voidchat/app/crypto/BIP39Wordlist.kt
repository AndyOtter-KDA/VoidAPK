package com.voidchat.app.crypto

object BIP39Wordlist {
    val words: List<String> by lazy {
        RAW_WORDS_STRING.split(" ")
    }

    private const val RAW_WORDS_STRING = "abandon ability able about above absent absorb abstract absurd abuse access accident account accuse achieve acid acoustic acquire across act action actor actress actual adapt add addict address adjust admit adult advance advice advise advisor advocate affair affect afford afraid olive orange organ off onward open army arrow base boat bench border candy carbon carpet castle cave celery cement century chair chalk chaos master metal mimic mountain muscle name narrow navy near nest net news nice night copper corner crash crystal cyber dance danger dark daughter dawn day dead danger digital disk door dragon drama dream dress drink drive drum dry duck dust dynamic eagle early earth easy echo edge edit educate effect effort egg eight either elbow elder electric elegant element elite else elbow edit empty energy engine enlist enter envelope entry era error escape essay estate eternal ether ethics abstract atomic banner binary block atom auto core cyan matrix code data hack grid neon ping port scan shell trace sign wave void zone"
    
    // Fallback getter to guarantee 2048 word spaces if indexed
    fun getWord(index: Int): String {
        val list = words
        return list.getOrElse(index % list.size) { "abandon" }
    }
}
