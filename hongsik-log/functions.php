<?php
/**
 * Theme setup and helpers.
 *
 * @package HongsikLog
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

function hongsik_log_setup() {
	load_theme_textdomain( 'hongsik-log', get_template_directory() . '/languages' );

	add_theme_support( 'automatic-feed-links' );
	add_theme_support( 'title-tag' );
	add_theme_support( 'post-thumbnails' );
	add_theme_support( 'responsive-embeds' );
	add_theme_support(
		'html5',
		array(
			'search-form',
			'comment-form',
			'comment-list',
			'gallery',
			'caption',
			'style',
			'script',
		)
	);

	register_nav_menus(
		array(
			'primary' => __( 'Primary Menu', 'hongsik-log' ),
		)
	);
}
add_action( 'after_setup_theme', 'hongsik_log_setup' );

function hongsik_log_enqueue_assets() {
	wp_enqueue_style(
		'hongsik-log-style',
		get_stylesheet_uri(),
		array(),
		filemtime( get_stylesheet_directory() . '/style.css' )
	);
}
add_action( 'wp_enqueue_scripts', 'hongsik_log_enqueue_assets' );

function hongsik_log_excerpt_length() {
	return 46;
}
add_filter( 'excerpt_length', 'hongsik_log_excerpt_length' );

function hongsik_log_excerpt_more() {
	return '...';
}
add_filter( 'excerpt_more', 'hongsik_log_excerpt_more' );

function hongsik_log_post_count() {
	$count = wp_count_posts( 'post' );
	return isset( $count->publish ) ? (int) $count->publish : 0;
}

function hongsik_log_get_about_page() {
	$page = get_page_by_path( 'about' );
	return $page instanceof WP_Post ? $page : null;
}

function hongsik_log_render_tags( $limit = 60 ) {
	$tags = get_tags(
		array(
			'hide_empty' => true,
			'orderby'    => 'count',
			'order'      => 'DESC',
			'number'     => $limit,
		)
	);

	echo '<ul class="tag-list">';
	echo '<li><a href="' . esc_url( home_url( '/' ) ) . '">all <span class="count">( ' . esc_html( hongsik_log_post_count() ) . ' )</span></a></li>';

	if ( empty( $tags ) ) {
		echo '</ul>';
		echo '<p class="tag-empty">' . esc_html__( '글을 발행하면 태그가 이곳에 쌓입니다.', 'hongsik-log' ) . '</p>';
		return;
	}

	foreach ( $tags as $tag ) {
		printf(
			'<li><a href="%1$s">%2$s <span class="count">( %3$d )</span></a></li>',
			esc_url( get_tag_link( $tag ) ),
			esc_html( $tag->name ),
			(int) $tag->count
		);
	}

	echo '</ul>';
}

function hongsik_log_render_post_tags() {
	$tags = get_the_tags();

	if ( empty( $tags ) ) {
		return;
	}

	echo '<div class="post-tags" aria-label="' . esc_attr__( 'Post tags', 'hongsik-log' ) . '">';
	foreach ( $tags as $tag ) {
		printf(
			'<a href="%1$s">%2$s</a>',
			esc_url( get_tag_link( $tag ) ),
			esc_html( $tag->name )
		);
	}
	echo '</div>';
}
