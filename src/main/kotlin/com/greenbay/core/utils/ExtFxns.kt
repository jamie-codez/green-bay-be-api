package com.greenbay.core.utils

fun String.randomAlphabetic(count: Int): String {
    val alphabets = arrayListOf('a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z')
    val random = arrayListOf<Char>()
    for (i in 0 until count){
        random.add(alphabets.random())
    }
    return random.toString().replace("[","").replace("]","").replace(",","").replace(" ","").trim()
}

fun String.randomNumeric(count: Int):String{
    val numbers = arrayListOf<Char>('1','2','3','4','5','6','7','8','9','0')
    val random = arrayListOf<Char>()
    for (i in 0 until count){
        random.add(numbers.random())
    }
    return random.toString().replace("[","").replace("]","").replace(",","")
}

fun String.append(string: String,attach:String):String{
    return "${string}$attach"
}
