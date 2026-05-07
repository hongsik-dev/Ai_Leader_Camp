<?php
/**
 * Header template.
 *
 * @package HongsikLog
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

$about_page = hongsik_log_get_about_page();
$github_url = get_theme_mod( 'hongsik_log_github_url', '' );
?>
<!doctype html>
<html <?php language_attributes(); ?>>
<head>
	<meta charset="<?php bloginfo( 'charset' ); ?>">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<?php wp_head(); ?>
</head>

<body <?php body_class(); ?>>
<?php wp_body_open(); ?>
<a class="screen-reader-text" href="#content"><?php esc_html_e( 'Skip to content', 'hongsik-log' ); ?></a>

<div class="site-shell">
	<header class="site-header">
		<p class="site-title">
			<a href="<?php echo esc_url( home_url( '/' ) ); ?>" rel="home">@<?php bloginfo( 'name' ); ?></a>
		</p>
		<p class="site-description"><?php bloginfo( 'description' ); ?></p>
		<nav class="site-nav" aria-label="<?php esc_attr_e( 'Primary navigation', 'hongsik-log' ); ?>">
			<a href="<?php echo esc_url( home_url( '/' ) ); ?>">POSTS <?php echo esc_html( hongsik_log_post_count() ); ?></a>
			<?php if ( $about_page ) : ?>
				<a href="<?php echo esc_url( get_permalink( $about_page ) ); ?>">ABOUT</a>
			<?php endif; ?>
			<?php if ( $github_url ) : ?>
				<a href="<?php echo esc_url( $github_url ); ?>" target="_blank" rel="me noopener noreferrer">GITHUB</a>
			<?php endif; ?>
			<a href="<?php echo esc_url( get_feed_link() ); ?>">RSS</a>
		</nav>
	</header>

	<main id="content">
