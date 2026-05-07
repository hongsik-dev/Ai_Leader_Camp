<?php
/**
 * Single post template.
 *
 * @package HongsikLog
 */

get_header();
?>

<?php
while ( have_posts() ) :
	the_post();
	?>
	<article <?php post_class( 'single-article' ); ?>>
		<header class="single-header">
			<h1 class="single-title"><?php the_title(); ?></h1>
			<div class="post-meta">
				<time datetime="<?php echo esc_attr( get_the_date( DATE_W3C ) ); ?>"><?php echo esc_html( get_the_date( 'F j, Y' ) ); ?></time>
				<span><?php echo esc_html( get_the_author() ); ?></span>
			</div>
			<?php hongsik_log_render_post_tags(); ?>
		</header>

		<div class="entry-content">
			<?php
			the_content();
			wp_link_pages(
				array(
					'before' => '<div class="page-links">',
					'after'  => '</div>',
				)
			);
			?>
		</div>

		<nav class="post-navigation" aria-label="<?php esc_attr_e( 'Post navigation', 'hongsik-log' ); ?>">
			<div class="previous"><?php previous_post_link( '%link', '&larr; %title' ); ?></div>
			<div class="next"><?php next_post_link( '%link', '%title &rarr;' ); ?></div>
		</nav>
	</article>
<?php endwhile; ?>

<?php
get_footer();
