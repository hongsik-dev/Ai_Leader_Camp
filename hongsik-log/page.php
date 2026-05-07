<?php
/**
 * Page template.
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
		</header>

		<div class="entry-content">
			<?php the_content(); ?>
		</div>
	</article>
<?php endwhile; ?>

<?php
get_footer();
