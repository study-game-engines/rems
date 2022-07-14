package me.anno.ui.editor.code.codemirror

import me.anno.io.ISaveable.Companion.registerCustomClass
import me.anno.io.files.InvalidRef
import me.anno.io.text.TextReader
import me.anno.io.text.TextWriter
import me.anno.io.utils.StringMap
import me.anno.maths.Maths.mixARGB
import me.anno.utils.Color.a
import me.anno.utils.ColorParsing
import me.anno.utils.OS
import me.anno.utils.strings.StringHelper.titlecase
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

object LanguageThemeLib {

    const val base = "[{\"class\":\"LanguageTheme\",\"i:*ptr\":1,"

    init {
        registerCustomClass(LanguageStyle())
        registerCustomClass(LanguageTheme())
    }

    fun read(str: String) = TextReader.readFirst<LanguageTheme>(base + str, InvalidRef)!!

    val Style3024Day = read("\"i[]:s\":[21,13478739,107090,106724,3814450,14363936,3814450,16641282,3814450,10578580,3814450,3814450,14363936,3814450,107090,10578580,15252432,3814450,6051925,3814450,3814450,3814450],\"S:name\":\"Style3024Day\",\"i:bg\":-526345,\"i:nCol\":-8356484,\"i:nBG0\":-526345,\"i:nLCol\":-526345,\"i:selBG\":-2697772,\"i:mbc\":-6198636,\"i:cursor\":-10725291}]")
    val Style3024Night = read("\"i[]:s\":[21,13478739,107090,106724,14079444,14363936,14079444,16641282,14079444,10578580,14079444,14079444,14363936,14079444,107090,10578580,15252432,14079444,8420732,14079444,14079444,14079444],\"S:name\":\"Style3024Night\",\"i:bg\":-16186624,\"i:nCol\":-10725291,\"i:nBG0\":-16186624,\"i:nLCol\":-16186624,\"i:selBG\":-12962766,\"i:mbc\":-1,\"i:cursor\":-8356484}]")
    val Abbott = read("\"i[]:s\":[21,33272623,14221188,14221188,9227760,47711312,14221188,14221188,15114995,14221188,47775620,15494297,47711312,14221188,2401543,16708532,14221188,16708532,2300948,4166129,14221188,14221188],\"S:name\":\"Abbott\",\"i:bg\":-14476268,\"i:nCol\":-2556028,\"i:nBG0\":-14476268,\"i:nLCol\":-14476268,\"i:selBG\":-14206720,\"i:mbc\":-14476268,\"i:cursor\":-14476268}]")
    val Abcdef = read("\"i[]:s\":[21,24804220,11259375,13290444,14613999,45647371,3189436,2276164,14613999,15631086,16776960,13408767,16768324,16702650,14548736,7829503,16775868,14613999,16711680,16774912,9079434,14613999],\"S:name\":\"Abcdef\",\"i:bg\":-15790321,\"i:nCol\":-1,\"i:nBG0\":-11184811,\"i:nLCol\":-13549231,\"i:selBG\":-11447983,\"i:mbc\":-15790321,\"i:cursor\":-16711936}]")
    val Ambiance = read("\"i[]:s\":[21,22369621,16758677,15651251,15131100,13477993,10066380,9411946,10326908,7917450,16420202,13805729,16704767,15651251,10192285,13598377,11192035,15131100,11477016,16776960,2409159,15131100],\"S:name\":\"Ambiance\",\"i:bg\":-14671840,\"i:nCol\":-15658735,\"i:nBG0\":-12763843,\"i:nLCol\":-11711155,\"i:selBG\":-13158601,\"i:mbc\":-16711936,\"i:cursor\":-8810008}]")
    val AyuDark = read("\"i[]:s\":[21,6449779,11776429,15757688,3783398,16748352,15119440,12769612,11776429,15119440,11776429,11776429,1655894,11776429,16757844,11436543,16772761,11776429,16724787,11776429,16316658,11776429],\"S:name\":\"AyuDark\",\"i:bg\":-16118252,\"i:nCol\":-12762547,\"i:nBG0\":-16118252,\"i:nLCol\":-16118252,\"i:selBG\":-14207161,\"i:mbc\":-1,\"i:cursor\":-1657776}]")
    val AyuMirage = read("\"i[]:s\":[21,22833011,13356230,15894393,6082534,16754521,16764006,12248702,13356230,16764006,13356230,13356230,6082534,15900276,16766336,11436543,16766336,13356230,16724787,13356230,3299945,13356230],\"S:name\":\"AyuMirage\",\"i:bg\":-14736336,\"i:nCol\":-13946821,\"i:nBG0\":-14736336,\"i:nLCol\":-14736336,\"i:selBG\":-13351590,\"i:mbc\":-1,\"i:cursor\":-13210}]")
    val Base16Dark = read("\"i[]:s\":[21,9393462,9480537,6987701,14737632,11288898,14737632,16039797,14737632,11171231,14737632,14737632,11288898,14737632,9480537,11171231,13796421,14737632,11579568,14737632,14737632,14737632],\"S:name\":\"Base16Dark\",\"i:bg\":-15395563,\"i:nCol\":-11513776,\"i:nBG0\":-15395563,\"i:nLCol\":-15395563,\"i:selBG\":-13619152,\"i:mbc\":-1,\"i:cursor\":-5197648}]")
    val Base16Light = read("\"i[]:s\":[21,9393462,9480537,6987701,2105376,11288898,2105376,16039797,2105376,11171231,2105376,2105376,11288898,2105376,9480537,11171231,13796421,2105376,5263440,2105376,2105376,2105376],\"S:name\":\"Base16Light\",\"i:bg\":-657931,\"i:nCol\":-5197648,\"i:nBG0\":-657931,\"i:nLCol\":-657931,\"i:selBG\":-2039584,\"i:mbc\":-657931,\"i:cursor\":-11513776}]")
    val Bespin = read("\"i[]:s\":[21,9662753,5553677,6203114,10328983,13593164,10328983,16379544,10328983,10192285,10328983,10328983,13593164,10328983,5553677,10192285,13598004,10328983,7960951,10328983,10328983,10328983],\"S:name\":\"Bespin\",\"i:bg\":-14147300,\"i:nCol\":-10066330,\"i:nBG0\":-14147300,\"i:nLCol\":-14147300,\"i:selBG\":-13225682,\"i:mbc\":-1,\"i:cursor\":-8816265}]")
    val Blackboard = read("\"i[]:s\":[21,11447982,16737280,16316664,16316664,16506413,9283278,6409788,6409788,14219836,16506413,14219836,9283278,16316664,9283278,14219836,9283278,16316664,16316664,16316664,16316664,16316664],\"S:name\":\"Blackboard\",\"i:bg\":-15986655,\"i:nCol\":-7829368,\"i:nBG0\":-15986655,\"i:nLCol\":-15986655,\"i:selBG\":-14337162,\"i:mbc\":-1,\"i:cursor\":-5789785}]")
    val Cobalt = read("\"i[]:s\":[21,35071,16777215,16777215,16777215,16772736,16777215,3856640,16777215,16777215,16777215,16751872,10420223,16777215,16744673,8674756,16777215,16777215,10296853,16777215,14211288,16777215],\"S:name\":\"Cobalt\",\"i:bg\":-16768448,\"i:nCol\":-3092272,\"i:nBG0\":-16768448,\"i:nLCol\":-5592406,\"i:selBG\":-5020359,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val Colorforth = read("\"i[]:s\":[21,15592941,7601920,15658734,16316664,16767232,55642,31743,16316664,50431,16316664,16776960,16760128,16316664,16774912,6316128,50273308,16316664,16711680,16774912,13421687,16316664],\"S:name\":\"Colorforth\",\"i:bg\":-16777216,\"i:nCol\":-4539718,\"i:nBG0\":-16121825,\"i:nLCol\":-5592406,\"i:selBG\":-13419181,\"i:mbc\":-16777216,\"i:cursor\":-1}]")
    val Darcula = read("\"i[]:s\":[21,23175505,11122630,11122630,9991850,46954546,16752217,6981465,6981465,6854587,11122630,12301609,123901781,16762477,6854587,13400114,27899846,11122630,12336956,6981465,11122630,11122630],\"S:name\":\"Darcula\",\"i:bg\":-13948117,\"i:nCol\":-13948117,\"i:nBG0\":-13552843,\"i:nLCol\":-13552843,\"i:selBG\":-14597501,\"i:mbc\":-4312,\"i:cursor\":-5654586}]")
    val Dracula = read("\"i[]:s\":[21,6451876,5307003,16777215,0,16742854,5307003,0,15858316,12424185,16742854,16316658,16742854,6740463,5307003,12424185,5307003,0,0,5307003],\"S:name\":\"Dracula\",\"i:bg\":-1,\"i:nCol\":-9598328,\"i:nBG0\":-14144970,\"i:nLCol\":-1,\"i:selBG\":-1,\"i:mbc\":-1,\"i:cursor\":-460560}]")
    val DuotoneDark = read("\"i[]:s\":[21,7104387,7104387,7104387,7104387,7104387,7104387,16758896,7104387,7104387,16756060,7104387,7104387,10127101,7104387,7104387,7104387,7104387,7104387,7104387,7104387,7104387],\"S:name\":\"DuotoneDark\",\"i:bg\":-14014668,\"i:nCol\":-11251353,\"i:nBG0\":-14014668,\"i:nLCol\":-14014668,\"i:selBG\":-11251353,\"i:mbc\":-1119233,\"i:cursor\":-21156}]")
    val DuotoneLight = read("\"i[]:s\":[21,11971994,11704162,11704162,11704162,11704162,11704162,11704162,11704162,11704162,1464799,11704162,11704162,11704162,11704162,11704162,11704162,11704162,7507915,11704162,11704162,11704162],\"S:name\":\"DuotoneLight\",\"i:bg\":-329483,\"i:nCol\":-3291983,\"i:nBG0\":-329483,\"i:nLCol\":-329483,\"i:selBG\":-1844018,\"i:mbc\":-329483,\"i:cursor\":-7099428}]")
    val Eclipse = read("\"i[]:s\":[21,4161375,0,192,0,41877589,3342506,2752767,16733440,1140292,0,16717591,1144576,0,204,2232729,255,0,16711680,5592405,13421687],\"S:name\":\"Eclipse\",\"i:bg\":-1,\"i:nCol\":-1,\"i:nBG0\":-1,\"i:nLCol\":-1,\"i:selBG\":-1510657,\"i:mbc\":-16777216,\"i:cursor\":-1}]")
    val Elegant = read("\"i[]:s\":[21,19031586,0,12259601,0,7811840,3342506,0,0,0,0,22369621,0,0,0,7824930,0,0,0,5592405],\"S:name\":\"Elegant\",\"i:bg\":-1,\"i:nCol\":-1,\"i:nBG0\":-1,\"i:nLCol\":-1,\"i:selBG\":-1510657,\"i:mbc\":-16777216,\"i:cursor\":-1}]")
    val ErlangDark = read("\"i[]:s\":[21,7829503,5307984,15597806,16777215,16772736,15641258,3856640,13421772,16765136,14505301,5308158,10420223,13421772,16744673,15807473,15628202,16777215,10296853,13421772,16751872,16777215],\"S:name\":\"ErlangDark\",\"i:bg\":-16768448,\"i:nCol\":-3092272,\"i:nBG0\":-16768448,\"i:nLCol\":-5592406,\"i:selBG\":-5020359,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val GruvboxDark = read("\"i[]:s\":[21,9601908,15457202,15457202,0,16271668,16678937,12106534,9355388,0,15457202,8627608,16678937,15457202,9355388,13862555,15457202,0,0,9355388],\"S:name\":\"GruvboxDark\",\"i:bg\":-1,\"i:nCol\":-8622236,\"i:nBG0\":-14145496,\"i:nLCol\":-1,\"i:selBG\":-7175308,\"i:mbc\":-14145496,\"i:cursor\":-1320014}]")
    val Hopscotch = read("\"i[]:s\":[21,11744520,9421118,1216703,14013397,14501452,14013397,16632921,14013397,13131388,14013397,14013397,14501452,14013397,9421118,13131388,16616217,14013397,9999512,14013397,14013397,14013397],\"S:name\":\"Hopscotch\",\"i:bg\":-13489871,\"i:nCol\":-8817799,\"i:nBG0\":-13489871,\"i:nLCol\":-13489871,\"i:selBG\":-12371134,\"i:mbc\":-1,\"i:cursor\":-6777704}]")
    val Icecoder = read("\"i[]:s\":[21,9937834,7124441,13377116,0,49213166,2182779,12175946,7124441,7124441,9533883,5592405,15263976,15658734,39321,14796654,12175946,0,14483456,5592405,13421687],\"S:name\":\"Icecoder\",\"i:bg\":-1,\"i:nCol\":-11184811,\"i:nBG0\":-14869221,\"i:nLCol\":-1,\"i:selBG\":-16764041,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val Idea = read("\"i[]:s\":[21,8421504,0,0,0,33554560,3342506,32768,32768,255,0,8421376,128,0,255,33554560,0,0,16711680,5592405,13421687],\"S:name\":\"Idea\",\"i:bg\":-1,\"i:nCol\":-1,\"i:nBG0\":-1,\"i:nLCol\":-1,\"i:selBG\":-1309,\"i:mbc\":-16777216,\"i:cursor\":-1}]")
    val Isotope = read("\"i[]:s\":[21,3342591,3407616,26367,14737632,16711680,14737632,16711833,14737632,13369599,14737632,14737632,16711680,14737632,3407616,13369599,16750848,14737632,12632256,14737632,14737632,14737632],\"S:name\":\"Isotope\",\"i:bg\":-16777216,\"i:nCol\":-8355712,\"i:nBG0\":-16777216,\"i:nLCol\":-16777216,\"i:selBG\":-12566464,\"i:mbc\":-1,\"i:cursor\":-4144960}]")
    val Juejin = read("\"i[]:s\":[21,40605,0,20640,0,0,12276152,0,0,0,0,0,1077248,0,16611137,0,1811184],\"S:name\":\"Juejin\",\"i:bg\":-460294,\"i:nCol\":-460294,\"i:nBG0\":-460294,\"i:nLCol\":-460294,\"i:selBG\":-460294,\"i:mbc\":-460294,\"i:cursor\":-460294}]")
    val LesserDark = read("\"i[]:s\":[21,6710886,14270348,6721945,8322174,5873407,16752217,12374649,16733440,11755085,9611100,7572595,6721945,9611100,8496341,12760176,16777215,8322174,10296853,5592405,15462375,8322174],\"S:name\":\"LesserDark\",\"i:bg\":-14277082,\"i:nCol\":-8947849,\"i:nBG0\":-14277082,\"i:nLCol\":-5592406,\"i:selBG\":-12237765,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val Liquibyte = read("\"i[]:s\":[21,32768,39413759,33587071,16777215,46170367,50310976,16744448,16777215,33619712,16777215,65280,50331392,43620761,46170367,46084144,50310976,16777215,16711680,50329344,13421687,16777215],\"S:name\":\"Liquibyte\",\"i:bg\":-16777216,\"i:nCol\":-10461088,\"i:nBG0\":-14277082,\"i:nLCol\":-11513776,\"i:selBG\":-12582912,\"i:mbc\":-16711936,\"i:cursor\":-1118482}]")
    val Lucario = read("\"i[]:s\":[21,6068429,16316658,16316658,0,16737601,7520349,0,15129460,13276415,6740463,16316658,16737601,16316658,6740463,12424185,7520349,0,0,7520349],\"S:name\":\"Lucario\",\"i:bg\":-1,\"i:nCol\":-460558,\"i:nBG0\":-13943216,\"i:nLCol\":-1,\"i:selBG\":-14404541,\"i:mbc\":-1,\"i:cursor\":-1652667}]")
    val MaterialDarker = read("\"i[]:s\":[21,5526612,15757688,15663103,15663103,13079274,16763755,12839053,15757688,16733040,9035263,16763755,16733040,13079274,13079274,16223340,8563455,15663103,16777215,14601067,15663103,15663103],\"S:name\":\"MaterialDarker\",\"i:bg\":-14606047,\"i:nCol\":-11250604,\"i:nBG0\":-14606047,\"i:nLCol\":-14606047,\"i:selBG\":-13750738,\"i:mbc\":-1,\"i:cursor\":-13312}]")
    val MaterialOcean = read("\"i[]:s\":[21,4606813,15757688,15663103,9409442,13079274,16763755,12839053,15757688,16733040,9035263,16763755,16733040,13079274,13079274,16223340,8563455,9409442,16777215,14601067,9409442,9409442],\"S:name\":\"MaterialOcean\",\"i:bg\":-15789798,\"i:nCol\":-12170403,\"i:nBG0\":-15789798,\"i:nLCol\":-15789798,\"i:selBG\":-14473671,\"i:mbc\":-1,\"i:cursor\":-13312}]")
    val MaterialPalenight = read("\"i[]:s\":[21,6778517,15757688,15663103,10923213,13079274,16763755,12839053,15757688,16733040,9035263,16763755,16733040,13079274,13079274,16223340,8563455,10923213,16777215,14601067,10923213,10923213],\"S:name\":\"MaterialPalenight\",\"i:bg\":-14078658,\"i:nCol\":-9998699,\"i:nBG0\":-14078658,\"i:nLCol\":-14078658,\"i:selBG\":-13157034,\"i:mbc\":-1,\"i:cursor\":-13312}]")
    val Material = read("\"i[]:s\":[21,5533306,15757688,15663103,15663103,13079274,16763755,12839053,15757688,16733040,9035263,16763755,16733040,13079274,13079274,16223340,8563455,15663103,16777215,14601067,15663103,15663103],\"S:name\":\"Material\",\"i:bg\":-14273992,\"i:nCol\":-11243910,\"i:nBG0\":-14273992,\"i:nLCol\":-14273992,\"i:selBG\":-13086380,\"i:mbc\":-1,\"i:cursor\":-13312}]")
    val Mbo = read("\"i[]:s\":[21,9803146,16777196,43206,16777196,16759080,16777196,16764780,16777196,43206,16777196,16777196,10346473,16777196,10346473,43206,16777196,16777196,16777196,16777196,50331644,16777196],\"S:name\":\"Mbo\",\"i:bg\":-13882324,\"i:nCol\":-2434342,\"i:nBG0\":-11645362,\"i:nLCol\":-13882324,\"i:selBG\":-9343902,\"i:mbc\":-18136,\"i:cursor\":-20}]")
    val MdnLike = read("\"i[]:s\":[21,7829367,30634,10066329,10066329,6447871,10188086,16807850,12413720,13269057,13477993,0,10057283,10027093,14072685,16750848,9283278,10066329,10066329,6723840,10066329,10066329],\"S:name\":\"MdnLike\",\"i:bg\":-1,\"i:nCol\":-5592406,\"i:nBG0\":-460552,\"i:nLCol\":-1,\"i:selBG\":-3342388,\"i:mbc\":-1,\"i:cursor\":-14540254}]")
    val Midnight = read("\"i[]:s\":[21,4361181,16755262,16755262,13757951,15218487,13757951,1949974,13757951,13757951,13757951,13757951,4474009,13757951,10936878,11436543,4513245,13757951,16316656,13757951,13757951,13757951],\"S:name\":\"Midnight\",\"i:bg\":-15787734,\"i:nCol\":-3092272,\"i:nBG0\":-15787734,\"i:nLCol\":-15787734,\"i:selBG\":-13546137,\"i:mbc\":-1,\"i:cursor\":-460560}]")
    val Monokai = read("\"i[]:s\":[21,7696734,16316658,10420223,16316658,16328306,6740463,15129460,16316658,11436543,16316658,16316658,16328306,16316658,10936878,11436543,16619295,16316658,16316656,16316658,16316658,16316658],\"S:name\":\"Monokai\",\"i:bg\":-14211038,\"i:nCol\":-3092272,\"i:nBG0\":-14211038,\"i:nLCol\":-14211038,\"i:selBG\":-11974594,\"i:mbc\":-1,\"i:cursor\":-460560}]")
    val Moxer = read("\"i[]:s\":[21,4146266,9344436,8504794,9344436,13921388,16763755,11723950,15757688,8168640,13921388,16763755,16733040,8504794,13079274,11115490,8504794,9344436,16777215,14601067,9344436,9344436],\"S:name\":\"Moxer\",\"i:bg\":-16184817,\"i:nCol\":-13289141,\"i:nBG0\":-16184817,\"i:nLCol\":-16184817,\"i:selBG\":-14605263,\"i:mbc\":-1,\"i:cursor\":-13312}]")
    val Neat = read("\"i[]:s\":[21,11176038,0,0,0,33554687,33585015,11149858,0,0,0,5592405,0,0,0,3385907],\"S:name\":\"Neat\",\"i:bg\":-1,\"i:nCol\":-1,\"i:nBG0\":-1,\"i:nLCol\":-1,\"i:selBG\":-1510657,\"i:mbc\":-16777216,\"i:cursor\":-1}]")
    val Neo = read("\"i[]:s\":[21,7698555,3029052,3029052,3029052,3029052,3029052,11755028,3029052,7685002,3029052,3029052,10236712,1930675,3029052,3029052,3029052,3029052,3029052,294245,3029052,3029052],\"S:name\":\"Neo\",\"i:bg\":-1,\"i:nCol\":-2039067,\"i:nBG0\":-1,\"i:nLCol\":-1,\"i:selBG\":-1,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val Night = read("\"i[]:s\":[21,8978641,16316664,16316664,16316664,5873407,16316664,3666250,16316664,16316664,16316664,7764194,10072831,16316664,16766208,8674756,16316664,16316664,10296853,16316664,9283278,16316664],\"S:name\":\"Night\",\"i:bg\":-16121825,\"i:nCol\":-460552,\"i:nBG0\":-16121825,\"i:nLCol\":-5592406,\"i:selBG\":-12303241,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val Nord = read("\"i[]:s\":[21,5002858,14212841,14212841,14212841,8495553,8495553,10731148,14212841,11833005,14212841,14212841,12542314,14212841,9419963,11833005,9419963,14212841,16316656,14212841,8495553,14212841],\"S:name\":\"Nord\",\"i:bg\":-13749184,\"i:nCol\":-11774358,\"i:nBG0\":-13749184,\"i:nLCol\":-13749184,\"i:selBG\":-12366754,\"i:mbc\":-1,\"i:cursor\":-460560}]")
    val OceanicNext = read("\"i[]:s\":[21,6648702,16316658,16316658,16316658,12948677,6740463,10078100,16316658,16355671,16316658,16316658,12948677,10078100,16316658,12948677,6724044,16316658,16316656,16316658,6271923,16316658],\"S:name\":\"OceanicNext\",\"i:bg\":-13614776,\"i:nCol\":-3092272,\"i:nBG0\":-13614776,\"i:nLCol\":-13614776,\"i:selBG\":-12496550,\"i:mbc\":-1,\"i:cursor\":-460560}]")
    val PandaSyntax = read("\"i[]:s\":[21,23554937,16758892,16751297,0,16741813,0,1702360,16758892,16758892,15987699,11568363,16723053,15987699,16758892,16723053,15132390],\"S:name\":\"PandaSyntax\",\"i:bg\":-1,\"i:nCol\":-1644826,\"i:nBG0\":-14079445,\"i:nLCol\":-1,\"i:selBG\":-1,\"i:mbc\":-1644826,\"i:cursor\":-1}]")
    val ParaisoDark = read("\"i[]:s\":[21,15297448,4765317,440047,12170928,15688021,12170928,16696344,12170928,8477604,12170928,12170928,15688021,12170928,4765317,8477604,16358165,12170928,9275015,12170928,12170928,12170928],\"S:name\":\"ParaisoDark\",\"i:bg\":-13689298,\"i:nCol\":-8950159,\"i:nBG0\":-13689298,\"i:nLCol\":-13689298,\"i:selBG\":-12504513,\"i:mbc\":-1,\"i:cursor\":-7502201}]")
    val ParaisoLight = read("\"i[]:s\":[21,15297448,4765317,440047,4272703,15688021,4272703,16696344,4272703,8477604,4272703,4272703,15688021,4272703,4765317,8477604,16358165,4272703,7827057,4272703,4272703,4272703],\"S:name\":\"ParaisoLight\",\"i:bg\":-1578533,\"i:nCol\":-7502201,\"i:nBG0\":-1578533,\"i:nLCol\":-1578533,\"i:selBG\":-4606288,\"i:mbc\":-1,\"i:cursor\":-8950159}]")
    val PastelOnDark = read("\"i[]:s\":[21,10929919,11449080,12500821,9409423,11449080,12697924,6728040,9409423,13421772,9409423,9409423,12697924,9409423,10936878,14585392,7699160,9409423,16316656,9409423,16316658,9409423],\"S:name\":\"PastelOnDark\",\"i:bg\":-13883353,\"i:nCol\":-7367793,\"i:nBG0\":-13357009,\"i:nLCol\":-13883353,\"i:selBG\":-11579310,\"i:mbc\":-7367793,\"i:cursor\":-5789785}]")
    val Railscasts = read("\"i[]:s\":[21,12358744,10863201,7183550,16052717,14305593,16052717,16762477,16052717,11973611,16052717,16052717,14305593,16052717,10863201,11973611,13400115,16052717,13946825,16052717,16052717,16052717],\"S:name\":\"Railscasts\",\"i:bg\":-13948117,\"i:nCol\":-10853250,\"i:nBG0\":-13948117,\"i:nLCol\":-13948117,\"i:selBG\":-14210763,\"i:mbc\":-1,\"i:cursor\":-2830391}]")
    val Rubyblue = read("\"i[]:s\":[21,26843545,16777215,16777215,16777215,16711935,16777215,15761479,16777215,16777215,16777215,16711935,8116263,16777215,8570592,16040459,16777215,16777215,11477016,16777215,16711935,16777215],\"S:name\":\"Rubyblue\",\"i:bg\":-15653835,\"i:nCol\":-1,\"i:nBG0\":-14727583,\"i:nLCol\":-12685177,\"i:selBG\":-13085073,\"i:mbc\":-65281,\"i:cursor\":-1}]")
    val Seti = read("\"i[]:s\":[21,4281179,5617115,10515652,13619921,15125865,10472022,13619921,5617115,13451077,10472022,5617115,5617115,10515652,10472022,13451077,5617115,13619921,13619921,10472022,13619921,13619921],\"S:name\":\"Seti\",\"i:bg\":-15395048,\"i:nCol\":-9598328,\"i:nBG0\":-15855342,\"i:nLCol\":-15395048,\"i:selBG\":-13816016,\"i:mbc\":-1,\"i:cursor\":-460560}]")
    val Shadowfox = read("\"i[]:s\":[21,9671571,12160767,7716863,14145499,16743913,16743913,7047679,7047679,7047679,11645363,9671571,7716863,8838772,16743913,16743913,7716863,11645363,16743913,7716863,7716863,11645363],\"S:name\":\"Shadowfox\",\"i:bg\":-14013906,\"i:nCol\":-7105645,\"i:nBG0\":-15987699,\"i:nLCol\":-15987699,\"i:selBG\":-13288632,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val Solarized = read("\"i[]:s\":[21,22572661,8623254,11897088,0,13323030,13842050,8755456,11897088,13842050,7107012,8755456,9675169,2793880,2793880,13842050,2793880,0,0,11897088,13323030],\"S:name\":\"Solarized\",\"i:bg\":-16304574,\"i:nCol\":-8153962,\"i:nBG0\":-1120043,\"i:nLCol\":-16304574,\"i:selBG\":-1120043,\"i:mbc\":-8021760,\"i:cursor\":-8286064}]")
    val Ssms = read("\"i[]:s\":[21,25600,0,0,0,255,0,16711680,16711935,0,0,0,0,0,0,11119017,0,11119017],\"S:name\":\"Ssms\",\"i:bg\":-1,\"i:nCol\":-16744320,\"i:nBG0\":-1,\"i:nLCol\":-4510,\"i:selBG\":-5384449,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val TheMatrix = read("\"i[]:s\":[21,13421772,16737996,13395711,65280,33589251,3342506,3381708,65280,16759119,10066329,13408767,16760128,6487968,16774912,3407871,10066380,65280,16711680,16774912,13421687,65280],\"S:name\":\"TheMatrix\",\"i:bg\":-16777216,\"i:nCol\":-1,\"i:nBG0\":-16751104,\"i:nLCol\":-16711936,\"i:selBG\":-13816531,\"i:mbc\":-16777216,\"i:cursor\":-16711936}]")
    val TomorrowNightBright = read("\"i[]:s\":[21,13794131,12175946,8038106,15395562,13979219,15395562,15189319,15395562,10578580,15395562,15395562,13979219,15395562,10079385,10578580,15174725,15395562,6974058,15395562,15395562,15395562],\"S:name\":\"TomorrowNightBright\",\"i:bg\":-16777216,\"i:nCol\":-12434878,\"i:nBG0\":-16777216,\"i:nLCol\":-16777216,\"i:selBG\":-12434878,\"i:mbc\":-1,\"i:cursor\":-9803158}]")
    val TomorrowNightEighties = read("\"i[]:s\":[21,13794131,10079385,6724044,13421772,15890298,13421772,16764006,13421772,10578580,13421772,13421772,15890298,13421772,10079385,10578580,16355671,13421772,6974058,13421772,13421772,13421772],\"S:name\":\"TomorrowNightEighties\",\"i:bg\":-16777216,\"i:nCol\":-11447983,\"i:nBG0\":-16777216,\"i:nLCol\":-16777216,\"i:selBG\":-13816531,\"i:mbc\":-1,\"i:cursor\":-9803158}]")
    val Ttcn = read("\"i[]:s\":[21,3355443,9118290,21930,0,33554432,0,25600,16733440,0,0,5592405,1144576,0,204,2232729,255,0,16711680,5592405,10066295],\"S:name\":\"Ttcn\",\"i:bg\":-1,\"i:nCol\":-1,\"i:nBG0\":-1,\"i:nLCol\":-1,\"i:selBG\":-1,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val Twilight = read("\"i[]:s\":[21,24606583,16250871,16250871,16250871,16379544,13477993,26189162,12413720,13269057,13477993,16250871,10057283,16250871,14072685,16763904,9283278,16250871,16250871,16250871,16250871,16250871],\"S:name\":\"Twilight\",\"i:bg\":-15461356,\"i:nCol\":-5592406,\"i:nBG0\":-14540254,\"i:nLCol\":-5592406,\"i:selBG\":-13487566,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val VibrantInk = read("\"i[]:s\":[21,41975936,16777215,16777215,16777215,13400114,9283278,10863196,16711680,16772760,8947848,14219836,9283278,16777215,9283278,16763904,9283278,16777215,16777215,16777215,16777215,16777215],\"S:name\":\"VibrantInk\",\"i:bg\":-16777216,\"i:nCol\":-3092272,\"i:nBG0\":-16768448,\"i:nLCol\":-5592406,\"i:selBG\":-13285060,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val XqDark = read("\"i[]:s\":[21,8421504,16777215,15658734,16316664,16760128,3342506,10481152,16316664,1140292,16316664,16776960,16760128,16316664,16774912,7113941,83886079,16316664,16711680,16774912,13421687,16316664],\"S:name\":\"XqDark\",\"i:bg\":-16121825,\"i:nCol\":-460552,\"i:nBG0\":-16121825,\"i:nLCol\":-5592406,\"i:selBG\":-14221190,\"i:mbc\":-1,\"i:cursor\":-1}]")
    val XqLight = read("\"i[]:s\":[21,16810239,0,0,0,39476397,8300118,16711680,0,1140292,0,16776960,4161407,0,8323199,7113941,67108864,0,16711680,8421504,13421687],\"S:name\":\"XqLight\",\"i:bg\":-1,\"i:nCol\":-1,\"i:nBG0\":-1,\"i:nLCol\":-1,\"i:selBG\":-1510657,\"i:mbc\":-16777216,\"i:cursor\":-1}]")
    val Yeti = read("\"i[]:s\":[21,13945022,5617115,10515652,13748672,10467694,10515652,13748672,9879768,10515652,10467694,9879768,9879768,10515652,10467694,10515652,5617115,13748672,13748672,9879768,13748672,13748672],\"S:name\":\"Yeti\",\"i:bg\":-1250584,\"i:nCol\":-5395546,\"i:nBG0\":-1711653,\"i:nLCol\":-1250584,\"i:selBG\":-2303790,\"i:mbc\":-1250584,\"i:cursor\":-3028544}]")
    val Yonce = read("\"i[]:s\":[21,23686512,30725332,31096238,10514378,42922,16532356,15129460,15964981,13948116,16532356,13948116,16532356,13948116,10514378,15964981,10019650,13948116,13948116,13948116,13948116,13948116],\"S:name\":\"Yonce\",\"i:bg\":-14935012,\"i:nCol\":-8947849,\"i:nBG0\":-14935012,\"i:nLCol\":-14935012,\"i:selBG\":-7917490,\"i:mbc\":-2829100,\"i:cursor\":-244860}]")
    val Zenburn = read("\"i[]:s\":[21,8363903,14659471,14474444,14474444,49340335,48028876,13407123,13407123,14474444,15790032,15785903,9691363,14659471,14659471,12577727,14474444,14474444,14474444,8173755,14474444,14474444],\"S:name\":\"Zenburn\",\"i:bg\":-12632257,\"i:nCol\":-12632257,\"i:nBG0\":-12632257,\"i:nLCol\":-12632257,\"i:selBG\":-11579569,\"i:mbc\":-12632257,\"i:cursor\":-1}]")

    val listOfAll = listOf(
        Style3024Day, Style3024Night, Abbott, Abcdef, Ambiance, AyuDark, AyuMirage, Base16Dark,
        Base16Light, Bespin, Blackboard, Cobalt, Colorforth, Darcula, Dracula, DuotoneDark, DuotoneLight, Eclipse,
        Elegant, ErlangDark, GruvboxDark, Hopscotch, Icecoder, Idea, Isotope, Juejin, LesserDark, Liquibyte, Lucario,
        MaterialDarker, MaterialOcean, MaterialPalenight, Material, Mbo, MdnLike, Midnight, Monokai, Moxer, Neat, Neo,
        Night, Nord, OceanicNext, PandaSyntax, ParaisoDark, ParaisoLight, PastelOnDark, Railscasts, Rubyblue, Seti,
        Shadowfox, Solarized, Ssms, TheMatrix, TomorrowNightBright, TomorrowNightEighties, Ttcn, Twilight, VibrantInk,
        XqDark, XqLight, Yeti, Yonce, Zenburn
    )

}