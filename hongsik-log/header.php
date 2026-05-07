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
$profile_image = get_template_directory_uri() . '/assets/profile-hongsik.png';
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
		<a class="profile-link" href="<?php echo esc_url( home_url( '/' ) ); ?>" rel="home">
			<img class="profile-avatar" src="<?php echo esc_url( $profile_image ); ?>" alt="<?php esc_attr_e( 'Hongsik profile mark', 'hongsik-log' ); ?>" width="128" height="128">
			<span class="profile-name">Hongsik</span>
		</a>
		<p class="site-title">@<?php bloginfo( 'name' ); ?></p>
		<p class="site-description"><?php bloginfo( 'description' ); ?></p>
		<nav class="site-nav" aria-label="<?php esc_attr_e( 'Primary navigation', 'hongsik-log' ); ?>">
			<a href="<?php echo esc_url( home_url( '/' ) ); ?>"><?php echo hongsik_log_icon( 'home' ); ?><span>Home</span></a>
			<a href="<?php echo esc_url( home_url( '/#tags' ) ); ?>"><?php echo hongsik_log_icon( 'tag' ); ?><span>태그</span></a>
			<a href="<?php echo esc_url( home_url( '/#archive' ) ); ?>"><?php echo hongsik_log_icon( 'archive' ); ?><span>아카이브</span></a>
			<?php if ( $about_page ) : ?>
				<a href="<?php echo esc_url( get_permalink( $about_page ) ); ?>"><?php echo hongsik_log_icon( 'about' ); ?><span>소개</span></a>
			<?php endif; ?>
			<?php if ( $github_url ) : ?>
				<a href="<?php echo esc_url( $github_url ); ?>" target="_blank" rel="me noopener noreferrer"><?php echo hongsik_log_icon( 'github' ); ?><span>GitHub</span></a>
			<?php endif; ?>
			<a href="<?php echo esc_url( get_feed_link() ); ?>"><?php echo hongsik_log_icon( 'rss' ); ?><span>RSS</span></a>
		</nav>
		<div id="tags" class="rail-tags">
			<p class="section-label">TAG LIST</p>
			<?php hongsik_log_render_tags( 10 ); ?>
		</div>
	</header>

	<main id="content">
