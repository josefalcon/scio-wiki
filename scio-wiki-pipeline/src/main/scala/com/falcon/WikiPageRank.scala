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

    val pages: SCollection[Page] = sc.typedBigQuery[Page]("SELECT id, title FROM [wiki-140001:wiki.pages]")

    val pageLinks: SCollection[PageLink] =
      sc.typedBigQuery[PageLink]("SELECT a_from_page_id as from_id, b_id as to_id FROM [wiki-140001:wiki.joined_pagelinks]")

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

    ranks
      .join(pages.keyBy(_.id))
      .values
      .map {
        case (rank, page) => PageRank(page.id, page.title, rank)
      }
      .saveAsTypedBigQuery(
        "wiki-140001:wiki.pageranks",
        WRITE_TRUNCATE,
        CREATE_IF_NEEDED)

    sc.close()
  }
}
