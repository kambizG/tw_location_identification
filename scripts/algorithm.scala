//#################################################################################################
// Social Graph Only
//#################################################################################################
:load /home/kambiz/data/tw_data_all_clean/tw_location_identification/scripts/extra.scala
extract_UMLP("tw_lo.txt", "tp_louvain", "UMLP", 5)

:load /home/kambiz/data/tw_data_all_clean/tw_location_identification/scripts/extra.scala
extract_UMLP("st_all_uniq.txt", "result/tp", "UMLP", 10)

:load /home/kambiz/data/tw_data_all_clean/tw_location_identification/scripts/extra.scala
extract_UMLP("stats.txt", "mf_partitions/tp", "UMLP", 6)

:load /home/kambiz/data/tw_data_all_clean/script/extra.scala
def extract_CDF_UMLP(in: String, res: String) = {
val UMLP = sc.textFile(in).map(_.split(",")).map(x => (x(0), ((x(1).toDouble,x(2).toDouble), x(3))))
val split = UMLP.map({case(u,(ml,p)) => (p,u)}).groupByKey().filter(_._2.size > 4).map({case(p,u) => (p, u.splitAt((u.size * 0.3).toInt))})
val train = split.map({case(p,(tr,ts)) => (tr)}).flatMap(x => x).map(x => (x,1)).reduceByKey(_+_)
val test = split.map({case(p,(tr,ts)) => (ts)}).flatMap(x => x).map(x => (x,1)).reduceByKey(_+_)
val PML = UMLP.join(train).map({case(u,((ml, p),x)) => (p, ml)}).groupByKey().map({case(p, ls) => (p, geometric_median(ls.toList))})
val U_PE = UMLP.join(test).map({case(u, ((ml,p),x)) => (p, (u, ml))}).join(PML).map({case(p, ((u, ml), pl)) => (u, geoDistance_points(ml, pl))})
val AED = U_PE.map({case(u,e) => (1, (e, 1))}).reduceByKey((a,b) => (a._1 + b._1, a._2 + b._2)).map(x => (x._2._1 * 1.0)/x._2._2).collect
val cnt = (U_PE.count / 2.0).toInt
val MED = U_PE.map(_._2).sortBy(x => x).take(cnt).drop(cnt -1)
val temp1 = U_PE.map(x => (Math.floor(x._2 * 10)/10, 1.0)).reduceByKey(_+_)
val temp2 = sc.parallelize(Array(0.0 to 60.0 by 0.1)).flatMap(x => x).map(x => (Math.floor(x*10)/10,0.0))
temp1.union(temp2).reduceByKey(_+_).sortBy(_._1).map(x => (x._1 + "\t" + x._2)).saveAsTextFile(res)
}

//#################################################################################################
// Social graph + Time
//#################################################################################################
:load /home/kambiz/data/tw_data_all_clean/tw_location_identification/scripts/extra.scala
extract_UDTMLP("st_all_uniq.txt", "result/tp", "UDTMLP", 10)

:load /home/kambiz/data/tw_data_all_clean/tw_location_identification/scripts/extra.scala
extract_UDTMLP("tw_lo.txt", "tp_louvain", "UDTMLP_1H", 5)

:load /home/kambiz/data/tw_data_all_clean/tw_location_identification/scripts/extra.scala

def extract_CDF_UDTMLP(in: String, res: String) = {
val UDTMLP = sc.textFile(in).map(_.split(",")).map(x => (x(0), (x(1), x(2), (x(3).toDouble,x(4).toDouble), x(5))))
val split = UDTMLP.map({case(u,(d,t, ml,p)) => (p,u)}).groupByKey().filter(_._2.size > 4).map({case(p,u) => (p, u.splitAt((u.size * 0.2).toInt))})
val train = split.map({case(p,(tr,ts)) => (tr)}).flatMap(x => x).map(x => (x,1)).reduceByKey(_+_)
val test = split.map({case(p,(tr,ts)) => (ts)}).flatMap(x => x).map(x => (x,1)).reduceByKey(_+_)
val PDTML = UDTMLP.join(train).map({case(u,((d, t, ml, p),x)) => ((p, d, t), ml)}).groupByKey().map({case(pdt, ls) => (pdt, geometric_median(ls.toList))})
val U_PE = UDTMLP.join(test).map({case(u, ((d, t, ml, p),x)) => ((p, d, t), (u, ml))}).join(PDTML).map({case(pdt, ((u, ml), pml)) => ((u,pdt), geoDistance_points(ml, pml))})
val AED = U_PE.map({case(u,e) => (1, (e, 1))}).reduceByKey((a,b) => (a._1 + b._1, a._2 + b._2)).map(x => (x._2._1 * 1.0)/x._2._2).collect
val cnt = (U_PE.count / 2.0).toInt
val MED = U_PE.map(_._2).sortBy(x => x).take(cnt).drop(cnt -1)

val temp1 = U_PE.map(x => (Math.floor(x._2 * 10)/10, 1.0)).reduceByKey(_+_)
val temp2 = sc.parallelize(Array(0.0 to 60.0 by 0.1)).flatMap(x => x).map(x => (Math.floor(x*10)/10,0.0))
temp1.union(temp2).reduceByKey(_+_).sortBy(_._1).map(x => (x._1 + "\t" + x._2)).saveAsTextFile(res)
}

extract_CDF_UDTMLP("UDTMLP.txt", "res_UDTMLP")
extract_CDF_UDTMLP("UDTMLP_1H.txt", "res_UDTMLP_1H")
extract_CDF_UDTMLP("UDTMLP_3H.txt", "res_UDTMLP_3H")

//#################################################################################################
// Social graph + Text
//#################################################################################################

//read stats, clean, remove stopwords, write for topic extraction
//##################
:load /home/kambiz/data/tw_data_all_clean/tw_location_identification/scripts/extra.scala
val sw = sc.textFile("../longstoplist.txt").collect
val stats = sc.textFile("tw_lo.txt").map(_.split(",",7)).map(x => (x(0), x(6)))
val cleanStats = stats.map(x => (x._1, cleanRemoveStopWords(x._2, sw, 2, 15)))
cleanStats.filter(_._2.split("\\s").size > 3).map(x => x._1 + "," + x._2).saveAsTextFile("stats_clean")

// Extract Topics and create sid_topic.txt
//##################
/*
dat stats_clean/part* >> stats_clean.txt
rm -r stats_clean/
mkdir LDA
cat stats_clean.txt | cut -d',' -f2 >> LDA/doc_info.txt
wc -l stats_clean.txt
sed -i '1s/^/6587169\n/' LDA/doc_info.txt
java -mx20g -cp bin:lib/args4j-2.0.6.jar jgibblda.LDA -est -alpha 0.05 -beta 0.01 -ntopics 200 -niters 5 -savestep 501 -twords 0 -dir /home/kambiz/data/tw_data_all_clean/lon/LDA/ -dfile doc_info.txt
*/
sc.textFile("LDA/model-final.theta").map(_.split("\\s").map(_.toDouble).zipWithIndex.maxBy(_._1)._2).saveAsTextFile("topics")
/*
cat topics/part-0* >> topics.txt
cat stats_clean.txt | cut -d',' -f1 >> sids.txt
paste sids.txt topcis.txt >> sid_topic.txt
*/
//##################
:load /home/kambiz/data/tw_data_all_clean/tw_location_identification/scripts/extra.scala
extract_UDTMLP("tw_lo.txt", "sid_topic.txt", "tp", "UTMLP", 5)

def extract_CDF_UTMLP(in: String, res: String) = {
val UTMLP = sc.textFile(in).map(_.split(",")).map(x => (x(0), (x(1), (x(2).toDouble,x(3).toDouble), x(4))))
val split = UTMLP.map({case(u,(top, ml,p)) => (p,u)}).groupByKey().filter(_._2.size > 4).map({case(p,u) => (p, u.splitAt((u.size * 0.2).toInt))})
val train = split.map({case(p,(tr,ts)) => (tr)}).flatMap(x => x).map(x => (x,1)).reduceByKey(_+_)
val test = split.map({case(p,(tr,ts)) => (ts)}).flatMap(x => x).map(x => (x,1)).reduceByKey(_+_)
val PTML = UTMLP.join(train).map({case(u,((top, ml, p),x)) => ((p, top), ml)}).groupByKey().map({case(ptop, mlList) => (ptop, geometric_median(mlList.toList))})
val U_PE = UTMLP.join(test).map({case(u, ((top, ml, p),x)) => ((p, top), (u, ml))}).join(PTML).map({case(ptop, ((u, ml), pml)) => ((u, ptop), geoDistance_points(ml, pml))})
//val AED = U_PE.map({case(u,e) => (1, (e, 1))}).reduceByKey((a,b) => (a._1 + b._1, a._2 + b._2)).map(x => (x._2._1 * 1.0)/x._2._2).collect
//val cnt = (U_PE.count / 2.0).toInt
//val MED = U_PE.map(_._2).sortBy(x => x).take(cnt).drop(cnt -1)
val temp1 = U_PE.map(x => (Math.floor(x._2 * 10)/10, 1.0)).reduceByKey(_+_)
val temp2 = sc.parallelize(Array(0.0 to 60.0 by 0.1)).flatMap(x => x).map(x => (Math.floor(x*10)/10,0.0))
temp1.union(temp2).reduceByKey(_+_).sortBy(_._1).map(x => (x._1 + "\t" + x._2)).saveAsTextFile(res)
}

:load /home/kambiz/data/tw_data_all_clean/tw_location_identification/scripts/extra.scala
extract_CDF_UTMLP("UTMLP_150.txt","res_UTMLP_150")
