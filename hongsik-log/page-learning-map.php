<?php
/**
 * Template Name: AI 학습맵
 *
 * @package HongsikLog
 */

get_header();

$published_posts = get_posts(
	array(
		'post_type'           => 'post',
		'post_status'         => 'publish',
		'posts_per_page'      => -1,
		'orderby'             => 'date',
		'order'               => 'ASC',
		'ignore_sticky_posts' => true,
	)
);

$nodes = array(
	'root' => array(
		'id'    => 'root',
		'label' => 'AI 리더 캠프',
		'type'  => 'root',
		'url'   => home_url( '/' ),
	),
);
$edges = array();
$category_groups = array();

foreach ( $published_posts as $map_post ) {
	$post_id = 'post-' . $map_post->ID;
	$post_categories = get_the_category( $map_post->ID );
	$post_tags = get_the_tags( $map_post->ID );
	$post_category_ids = array();

	$nodes[ $post_id ] = array(
		'id'          => $post_id,
		'label'       => get_the_title( $map_post ),
		'type'        => 'post',
		'url'         => get_permalink( $map_post ),
		'date'        => get_the_date( 'Y.m.d', $map_post ),
		'categoryIds' => array(),
	);

	foreach ( $post_categories as $category ) {
		$category_id = 'cat-' . $category->term_id;
		$post_category_ids[] = $category_id;

		if ( ! isset( $nodes[ $category_id ] ) ) {
			$nodes[ $category_id ] = array(
				'id'    => $category_id,
				'label' => $category->name,
				'type'  => 'category',
				'url'   => get_category_link( $category ),
			);
			$edges[] = array(
				'source' => 'root',
				'target' => $category_id,
			);
		}

		$edges[] = array(
			'source' => $category_id,
			'target' => $post_id,
		);

		if ( ! isset( $category_groups[ $category->term_id ] ) ) {
			$category_groups[ $category->term_id ] = array(
				'term'  => $category,
				'posts' => array(),
			);
		}
		$category_groups[ $category->term_id ]['posts'][] = $map_post;
	}

	$nodes[ $post_id ]['categoryIds'] = $post_category_ids;

	if ( ! empty( $post_tags ) ) {
		foreach ( $post_tags as $tag ) {
			$tag_id = 'tag-' . $tag->term_id;

			if ( ! isset( $nodes[ $tag_id ] ) ) {
				$nodes[ $tag_id ] = array(
					'id'    => $tag_id,
					'label' => $tag->name,
					'type'  => 'tag',
					'url'   => get_tag_link( $tag ),
				);
			}

			$edges[] = array(
				'source' => $post_id,
				'target' => $tag_id,
			);
		}
	}
}

uasort(
	$category_groups,
	function ( $left, $right ) {
		return strcasecmp( $left['term']->name, $right['term']->name );
	}
);

$graph_data = array(
	'nodes' => array_values( $nodes ),
	'edges' => $edges,
);
?>

<article class="learning-map-page">
	<header class="archive-heading">
		<p class="archive-kicker">KNOWLEDGE GRAPH</p>
		<h1 class="archive-title"><?php the_title(); ?></h1>
		<p class="map-intro">발행된 학습 기록을 카테고리와 태그로 연결한 지식 그래프입니다. 노드를 드래그하거나 클릭해서 글과 주제의 관계를 살펴볼 수 있습니다.</p>
	</header>

	<section class="map-panel" aria-label="<?php esc_attr_e( 'AI learning knowledge graph', 'hongsik-log' ); ?>">
		<div class="map-toolbar" aria-label="<?php esc_attr_e( 'Graph filters', 'hongsik-log' ); ?>">
			<button class="is-active" type="button" data-map-filter="all">전체</button>
			<?php foreach ( $category_groups as $category_group ) : ?>
				<button type="button" data-map-filter="<?php echo esc_attr( 'cat-' . $category_group['term']->term_id ); ?>">
					<?php echo esc_html( $category_group['term']->name ); ?>
				</button>
			<?php endforeach; ?>
		</div>
		<div class="map-stage">
			<canvas id="learning-map-canvas" width="960" height="580"></canvas>
			<div class="map-hint" id="learning-map-hint">노드를 클릭하면 해당 글이나 목록으로 이동합니다.</div>
		</div>
		<div class="map-legend" aria-label="<?php esc_attr_e( 'Graph legend', 'hongsik-log' ); ?>">
			<span><i class="root"></i>캠프</span>
			<span><i class="category"></i>카테고리</span>
			<span><i class="post"></i>글</span>
			<span><i class="tag"></i>태그</span>
		</div>
	</section>

	<script type="application/json" id="hongsik-learning-map-data">
		<?php echo wp_json_encode( $graph_data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES ); ?>
	</script>

	<section class="map-list" aria-label="<?php esc_attr_e( 'Posts grouped by category', 'hongsik-log' ); ?>">
		<div class="archive-heading compact">
			<p class="archive-kicker">POST INDEX</p>
			<h2 class="archive-title">카테고리별 글 목록</h2>
		</div>

		<?php if ( empty( $category_groups ) ) : ?>
			<p class="tag-empty">발행된 학습 기록이 쌓이면 이곳에 자동으로 표시됩니다.</p>
		<?php else : ?>
			<div class="category-index">
				<?php foreach ( $category_groups as $category_group ) : ?>
					<section class="category-block">
						<h3>
							<a href="<?php echo esc_url( get_category_link( $category_group['term'] ) ); ?>">
								<?php echo esc_html( $category_group['term']->name ); ?>
							</a>
						</h3>
						<ol>
							<?php foreach ( $category_group['posts'] as $category_post ) : ?>
								<li>
									<a href="<?php echo esc_url( get_permalink( $category_post ) ); ?>"><?php echo esc_html( get_the_title( $category_post ) ); ?></a>
									<time datetime="<?php echo esc_attr( get_the_date( DATE_W3C, $category_post ) ); ?>"><?php echo esc_html( get_the_date( 'Y.m.d', $category_post ) ); ?></time>
								</li>
							<?php endforeach; ?>
						</ol>
					</section>
				<?php endforeach; ?>
			</div>
		<?php endif; ?>
	</section>
</article>

<?php
get_footer();
