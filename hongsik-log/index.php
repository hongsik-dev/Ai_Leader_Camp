<?php
/**
 * Main post index.
 *
 * @package HongsikLog
 */

get_header();
?>

<div class="layout">
	<?php get_template_part( 'sidebar-tags' ); ?>

	<section class="content-area">
		<?php if ( is_home() && ! is_paged() ) : ?>
			<div class="archive-heading">
				<p class="archive-kicker">AI LEADER CAMP LOG</p>
				<h1 class="archive-title">배운 것, 막힌 것, 끝까지 해결한 것을 차곡차곡 기록합니다.</h1>
			</div>
		<?php elseif ( is_archive() || is_search() ) : ?>
			<div class="archive-heading">
				<p class="archive-kicker">
					<?php echo is_search() ? esc_html__( 'SEARCH', 'hongsik-log' ) : esc_html__( 'ARCHIVE', 'hongsik-log' ); ?>
				</p>
				<h1 class="archive-title">
					<?php
					if ( is_search() ) {
						printf(
							/* translators: %s: Search query. */
							esc_html__( 'Search results for "%s"', 'hongsik-log' ),
							esc_html( get_search_query() )
						);
					} else {
						the_archive_title();
					}
					?>
				</h1>
			</div>
		<?php endif; ?>

		<?php if ( have_posts() ) : ?>
			<div class="post-list">
				<?php
				while ( have_posts() ) :
					the_post();
					?>
					<article <?php post_class( 'post-card' ); ?>>
						<h2 class="post-card-title">
							<a href="<?php the_permalink(); ?>"><?php the_title(); ?></a>
						</h2>
						<div class="post-meta">
							<time datetime="<?php echo esc_attr( get_the_date( DATE_W3C ) ); ?>"><?php echo esc_html( get_the_date( 'F j, Y' ) ); ?></time>
							<span><?php echo esc_html( get_the_author() ); ?></span>
						</div>
						<p class="post-excerpt"><?php echo esc_html( wp_strip_all_tags( get_the_excerpt() ) ); ?></p>
						<?php hongsik_log_render_post_tags(); ?>
					</article>
				<?php endwhile; ?>
			</div>

			<nav class="pagination" aria-label="<?php esc_attr_e( 'Posts navigation', 'hongsik-log' ); ?>">
				<div><?php previous_posts_link( __( 'Newer posts', 'hongsik-log' ) ); ?></div>
				<div><?php next_posts_link( __( 'Older posts', 'hongsik-log' ) ); ?></div>
			</nav>
		<?php else : ?>
			<div class="empty-state">
				<p><?php esc_html_e( '아직 발행된 글이 없습니다. 첫 공부 기록을 남겨보세요.', 'hongsik-log' ); ?></p>
			</div>
		<?php endif; ?>
	</section>
</div>

<?php
get_footer();
