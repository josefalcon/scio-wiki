package com.falcon

import com.spotify.scio._
import com.spotify.scio.bigquery.types.BigQueryType
import com.spotify.scio.experimental._
import com.spotify.scio.values.SCollection

object WikiPageRank {

  @BigQueryType.toTable
  case class Page(id: String, title: String)

  @BigQueryType.toTable
  case class PageLink(from_id: String, to_id: String)

  @BigQueryType.toTable
  case class PageRank(id: String, title: String, rank: Double)

  val iterations = 10
  val dampingFactor = 0.85
  
  def main(cmdlineArgs: Array[String]): Unit = {
    val (sc, args) = ContextAndArgs(cmdlineArgs)
    val project = args("project_id")
    val dataset = args("dataset_id")

    val pages: SCollection[Page] = sc.typedBigQuery[Page](s"SELECT id, title FROM [${project}:${dataset}.pages]")

    val pageLinks: SCollection[PageLink] =
      sc.typedBigQuery[PageLink](s"SELECT from_page_id as from_id, to_page_id as to_id FROM [${project}:${dataset}.joined_pagelinks]")

    val links: SCollection[(String, Iterable[String])] = pageLinks.map(p => (p.from_id, p.to_id)).groupByKey

    var ranks = links.mapValues(_ => 1.0)

    // basic page rank
    // https://github.com/spotify/big-data-rosetta-code/blob/f22eef060014aecced8390bd9c38f00fedc71b7b/src/main/scala/com/spotify/bdrc/pipeline/PageRank.scala
    for (i <- 1 to iterations) {
      val contribs = links
        .join(ranks)
        .values
        .flatMap {
          case (ids, rank) =>
            val size = ids.size
            ids.map((_, rank / size))
        }

      ranks = contribs
        .sumByKey
        .mapValues((1 - dampingFactor) + dampingFactor * _)
    }

    pages.keyBy(_.id)
      .leftOuterJoin(ranks)
      .values
      .map {
        case (page, maybeRank) => PageRank(page.id, page.title, maybeRank.getOrElse(0.0))
      }
      .saveAsTypedBigQuery(
        s"${project}:${dataset}.pageranks",
        WRITE_TRUNCATE,
        CREATE_IF_NEEDED)

    sc.close()
  }
}
