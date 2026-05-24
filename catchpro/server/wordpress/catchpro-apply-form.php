<?php
/**
 * Plugin Name: CatchPro Apply Form
 * Description: CatchPro Pro 신청 폼과 관리자 저장소를 제공합니다.
 * Version: 1.0.3
 * Author: CatchPro
 */

if (!defined('ABSPATH')) {
    exit;
}

add_action('init', 'catchpro_apply_register_post_type');
add_shortcode('catchpro_apply_form', 'catchpro_apply_render_form');
add_action('admin_post_nopriv_catchpro_apply_submit', 'catchpro_apply_handle_submit');
add_action('admin_post_catchpro_apply_submit', 'catchpro_apply_handle_submit');
add_action('admin_menu', 'catchpro_apply_register_settings_page');
add_action('admin_init', 'catchpro_apply_register_settings');
add_filter('body_class', 'catchpro_apply_body_class');

function catchpro_apply_register_post_type(): void
{
    register_post_type('catchpro_application', [
        'labels' => [
            'name' => 'CatchPro 신청',
            'singular_name' => 'CatchPro 신청',
            'menu_name' => 'CatchPro 신청',
            'add_new_item' => '신청 직접 추가',
            'edit_item' => '신청 내용 보기',
            'view_item' => '신청 내용 보기',
            'search_items' => '신청 검색',
            'not_found' => '신청 내역이 없습니다.',
        ],
        'public' => false,
        'show_ui' => true,
        'show_in_menu' => true,
        'menu_icon' => 'dashicons-location-alt',
        'capability_type' => 'post',
        'supports' => ['title', 'editor'],
    ]);
}

function catchpro_apply_field(string $key): string
{
    return isset($_POST[$key]) ? sanitize_text_field(wp_unslash($_POST[$key])) : '';
}

function catchpro_apply_textarea(string $key): string
{
    return isset($_POST[$key]) ? sanitize_textarea_field(wp_unslash($_POST[$key])) : '';
}

function catchpro_apply_body_class(array $classes): array
{
    $post = get_post();
    if ((is_page('catchpro-pro-apply')) || ($post && has_shortcode((string) $post->post_content, 'catchpro_apply_form'))) {
        $classes[] = 'catchpro-apply-page';
    }

    return array_values(array_unique($classes));
}

function catchpro_apply_register_settings_page(): void
{
    add_options_page(
        'CatchPro 신청 설정',
        'CatchPro 신청',
        'manage_options',
        'catchpro-apply-settings',
        'catchpro_apply_render_settings_page'
    );
}

function catchpro_apply_register_settings(): void
{
    register_setting('catchpro_apply_settings', 'catchpro_kakao_url', [
        'type' => 'string',
        'sanitize_callback' => 'esc_url_raw',
        'default' => '',
    ]);
}

function catchpro_apply_render_settings_page(): void
{
    if (!current_user_can('manage_options')) {
        return;
    }
    ?>
    <div class="wrap">
        <h1>CatchPro 신청 설정</h1>
        <form method="post" action="options.php">
            <?php settings_fields('catchpro_apply_settings'); ?>
            <table class="form-table" role="presentation">
                <tr>
                    <th scope="row"><label for="catchpro_kakao_url">카카오톡 상담 URL</label></th>
                    <td>
                        <input
                            id="catchpro_kakao_url"
                            name="catchpro_kakao_url"
                            type="url"
                            class="regular-text"
                            value="<?php echo esc_attr((string) get_option('catchpro_kakao_url', '')); ?>"
                            placeholder="https://pf.kakao.com/... 또는 https://open.kakao.com/o/..."
                        >
                        <p class="description">카카오 채널 채팅 URL이나 오픈채팅 URL을 입력하면 신청 페이지의 카카오톡 상담 버튼이 활성화됩니다.</p>
                    </td>
                </tr>
            </table>
            <?php submit_button('설정 저장'); ?>
        </form>
    </div>
    <?php
}

function catchpro_apply_get_kakao_url(array $atts): string
{
    $shortcode_url = isset($atts['kakao_url']) ? esc_url_raw((string) $atts['kakao_url']) : '';
    if ($shortcode_url !== '') {
        return $shortcode_url;
    }

    $option_url = get_option('catchpro_kakao_url', '');
    return is_string($option_url) ? esc_url_raw($option_url) : '';
}

function catchpro_apply_render_form($raw_atts = []): string
{
    $atts = shortcode_atts([
        'kakao_url' => '',
    ], is_array($raw_atts) ? $raw_atts : [], 'catchpro_apply_form');

    $is_done = isset($_GET['catchpro_apply']) && $_GET['catchpro_apply'] === 'success';
    $kakao_url = catchpro_apply_get_kakao_url($atts);
    $has_kakao = $kakao_url !== '';

    ob_start();
    ?>
    <script>
        document.documentElement.classList.add('catchpro-js');
        document.body.classList.add('catchpro-apply-page');
    </script>
    <style>
        .site-shell,
        body.catchpro-apply-page .site-shell {
            display: block !important;
            max-width: none !important;
            width: 100% !important;
            margin: 0 !important;
        }
        .site-header,
        .single-header,
        body.catchpro-apply-page .site-header,
        body.catchpro-apply-page .single-header {
            display: none !important;
        }
        #content,
        .single-article,
        .entry-content,
        body.catchpro-apply-page #content,
        body.catchpro-apply-page .single-article,
        body.catchpro-apply-page .entry-content {
            max-width: none !important;
            width: 100% !important;
            margin: 0 !important;
            padding: 0 !important;
        }
        .entry-content > *,
        body.catchpro-apply-page .entry-content > * {
            max-width: none !important;
        }
        .catchpro-apply {
            --cp-ink: #15191f;
            --cp-muted: #5d6472;
            --cp-line: #dfe3ea;
            --cp-soft: #f7f8fb;
            --cp-blue: #2d55ff;
            --cp-blue-dark: #183fff;
            --cp-green: #158f72;
            --cp-radius: 8px;
            color: var(--cp-ink);
            font-family: inherit;
            line-height: 1.65;
            background: #fff;
        }
        .catchpro-apply * {
            box-sizing: border-box;
        }
        @keyframes catchproFadeUp {
            from {
                opacity: 0;
                transform: translateY(14px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
        @keyframes catchproFadeIn {
            from {
                opacity: 0;
            }
            to {
                opacity: 1;
            }
        }
        .catchpro-apply__hero {
            background: #fff;
            color: var(--cp-ink);
            border-bottom: 1px solid var(--cp-line);
            margin: 0 calc(50% - 50vw) 0;
            padding: 64px max(24px, calc((100vw - 1040px) / 2)) 52px;
        }
        .catchpro-apply__eyebrow {
            margin: 0 0 14px;
            color: var(--cp-blue);
            font-size: 15px;
            font-weight: 700;
            letter-spacing: 0;
            opacity: 0;
            animation: catchproFadeUp 0.55s ease forwards;
        }
        .catchpro-apply__hero h1 {
            max-width: 780px;
            margin: 0;
            color: var(--cp-ink);
            font-size: clamp(34px, 5vw, 58px);
            line-height: 1.12;
            letter-spacing: 0;
            opacity: 0;
            animation: catchproFadeUp 0.62s ease 0.08s forwards;
        }
        .catchpro-apply__lead {
            max-width: 720px;
            margin: 22px 0 0;
            color: var(--cp-muted);
            font-size: 19px;
            opacity: 0;
            animation: catchproFadeUp 0.62s ease 0.16s forwards;
        }
        .catchpro-apply__actions {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            margin-top: 30px;
            opacity: 0;
            animation: catchproFadeUp 0.62s ease 0.24s forwards;
        }
        .catchpro-apply__button {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-height: 46px;
            padding: 0 18px;
            border-radius: var(--cp-radius);
            background: var(--cp-blue);
            color: #fff;
            font-weight: 800;
            text-decoration: none;
            transition: transform 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease, border-color 0.18s ease;
        }
        .catchpro-apply__button:hover {
            transform: translateY(-1px);
            box-shadow: 0 8px 18px rgba(45, 85, 255, 0.16);
        }
        .catchpro-apply__button--ghost {
            background: #fff;
            color: var(--cp-blue);
            border: 1px solid var(--cp-line);
        }
        .catchpro-apply__button--kakao {
            background: #fee500;
            color: #1d1d1d;
        }
        .catchpro-apply__button--disabled {
            background: #f2f4f8;
            color: #717888;
            border: 1px solid var(--cp-line);
            cursor: default;
        }
        .catchpro-apply__button--disabled:hover {
            transform: none;
            box-shadow: none;
        }
        .catchpro-apply__wrap {
            max-width: 1040px;
            margin: 0 auto;
            padding: 42px 0 64px;
        }
        .catchpro-apply__notice {
            border: 1px solid #b8e2cc;
            border-left: 5px solid var(--cp-green);
            border-radius: var(--cp-radius);
            padding: 16px 18px;
            background: #f5fbf8;
            color: #194932;
            font-weight: 700;
            margin-bottom: 22px;
            animation: catchproFadeIn 0.4s ease both;
        }
        .catchpro-apply__grid {
            display: grid;
            grid-template-columns: repeat(3, minmax(0, 1fr));
            gap: 16px;
            margin: 28px 0 36px;
        }
        .catchpro-apply__panel {
            border: 1px solid var(--cp-line);
            border-radius: var(--cp-radius);
            padding: 22px;
            background: #fff;
            box-shadow: none;
            opacity: 1;
            transform: none;
            transition: opacity 0.5s ease, transform 0.5s ease, border-color 0.18s ease, background-color 0.18s ease;
        }
        .catchpro-js .catchpro-apply__panel {
            opacity: 0;
            transform: translateY(24px);
        }
        .catchpro-js .catchpro-apply__panel.is-visible {
            opacity: 1;
            transform: translateY(0);
        }
        .catchpro-js .catchpro-apply__panel:nth-child(2) {
            transition-delay: 0.08s;
        }
        .catchpro-js .catchpro-apply__panel:nth-child(3) {
            transition-delay: 0.16s;
        }
        .catchpro-apply__panel:hover {
            transform: translateY(-2px);
            border-color: #c4ccff;
            background: #fcfdff;
        }
        .catchpro-apply__panel h2,
        .catchpro-apply__panel h3 {
            margin: 0 0 10px;
            font-size: 22px;
            line-height: 1.32;
            letter-spacing: 0;
        }
        .catchpro-apply__panel p {
            margin: 0;
            color: var(--cp-muted);
            font-size: 16px;
            line-height: 1.72;
        }
        .catchpro-apply__feature-label {
            display: inline-flex;
            align-items: center;
            min-height: 26px;
            margin-bottom: 12px;
            padding: 0 10px;
            border: 1px solid rgba(45, 85, 255, 0.2);
            border-radius: 999px;
            background: #f4f6ff;
            color: var(--cp-blue);
            font-size: 13px;
            font-weight: 900;
        }
        .catchpro-apply__accent {
            color: var(--cp-blue);
            font-weight: 900;
        }
        .catchpro-apply__section-title {
            margin: 0 0 10px;
            font-size: 28px;
            letter-spacing: 0;
        }
        .catchpro-apply__section-copy {
            max-width: 760px;
            margin: 0 0 22px;
            color: var(--cp-muted);
        }
        .catchpro-apply__flow {
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 1px;
            overflow: hidden;
            border: 1px solid var(--cp-line);
            border-radius: var(--cp-radius);
            background: var(--cp-line);
            margin-bottom: 40px;
        }
        .catchpro-apply__step {
            background: #fff;
            padding: 18px;
            opacity: 0;
            animation: catchproFadeUp 0.5s ease forwards;
        }
        .catchpro-apply__step:nth-child(2) {
            animation-delay: 0.05s;
        }
        .catchpro-apply__step:nth-child(3) {
            animation-delay: 0.1s;
        }
        .catchpro-apply__step:nth-child(4) {
            animation-delay: 0.15s;
        }
        .catchpro-apply__step strong {
            display: block;
            margin-bottom: 6px;
            color: var(--cp-blue);
        }
        .catchpro-apply__form {
            border: 1px solid var(--cp-line);
            border-radius: var(--cp-radius);
            background: #fff;
            padding: 28px;
            box-shadow: none;
            opacity: 0;
            animation: catchproFadeUp 0.55s ease 0.08s forwards;
        }
        .catchpro-apply__fields {
            display: grid;
            grid-template-columns: repeat(2, minmax(0, 1fr));
            gap: 16px;
        }
        .catchpro-apply__field {
            display: flex;
            flex-direction: column;
            gap: 7px;
        }
        .catchpro-apply__field--wide {
            grid-column: 1 / -1;
        }
        .catchpro-apply label {
            font-weight: 800;
            color: #242a33;
        }
        .catchpro-apply input,
        .catchpro-apply select,
        .catchpro-apply textarea {
            width: 100%;
            min-height: 46px;
            border: 1px solid #cfd6df;
            border-radius: var(--cp-radius);
            padding: 10px 12px;
            color: var(--cp-ink);
            background: #fff;
            font: inherit;
            transition: border-color 0.16s ease, box-shadow 0.16s ease, background-color 0.16s ease;
        }
        .catchpro-apply textarea {
            min-height: 120px;
            resize: vertical;
        }
        .catchpro-apply input:focus,
        .catchpro-apply select:focus,
        .catchpro-apply textarea:focus {
            outline: 3px solid rgba(45, 85, 255, 0.14);
            border-color: var(--cp-blue);
        }
        .catchpro-apply__consent {
            display: flex;
            gap: 10px;
            align-items: flex-start;
            margin: 18px 0 22px;
            color: var(--cp-muted);
            font-size: 15px;
        }
        .catchpro-apply__consent input {
            width: 20px;
            min-height: 20px;
            margin-top: 3px;
        }
        .catchpro-apply__submit {
            width: 100%;
            min-height: 52px;
            border: 0;
            border-radius: var(--cp-radius);
            background: var(--cp-blue);
            color: #fff;
            cursor: pointer;
            font: inherit;
            font-size: 17px;
            font-weight: 900;
            transition: transform 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease;
        }
        .catchpro-apply__submit:hover {
            background: #1558b8;
            transform: translateY(-1px);
            box-shadow: 0 10px 20px rgba(45, 85, 255, 0.18);
        }
        .catchpro-apply__contact {
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            gap: 14px;
            align-items: center;
            margin: 24px 0;
            padding: 18px;
            border: 1px solid #f0de5b;
            border-radius: var(--cp-radius);
            background: #fffdf0;
            opacity: 0;
            animation: catchproFadeUp 0.5s ease forwards;
        }
        .catchpro-apply__contact strong {
            display: block;
            margin-bottom: 4px;
            color: #2b2610;
        }
        .catchpro-apply__contact span {
            color: #665d35;
            font-size: 15px;
        }
        .catchpro-apply__contact-link {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-height: 44px;
            padding: 0 16px;
            border-radius: var(--cp-radius);
            background: #fee500;
            color: #1d1d1d;
            font-weight: 900;
            text-decoration: none;
            white-space: nowrap;
            transition: transform 0.18s ease, box-shadow 0.18s ease;
        }
        .catchpro-apply__contact-link:hover {
            transform: translateY(-1px);
            box-shadow: 0 8px 16px rgba(150, 124, 0, 0.16);
        }
        .catchpro-apply__contact-link--disabled {
            background: #e6e0bd;
            color: #706941;
            cursor: default;
        }
        .catchpro-apply__contact-link--disabled:hover {
            transform: none;
            box-shadow: none;
        }
        .catchpro-apply__fine {
            margin: 14px 0 0;
            color: var(--cp-muted);
            font-size: 14px;
        }
        .catchpro-apply__hidden {
            display: none;
        }
        @media (max-width: 820px) {
            .catchpro-apply__hero {
                padding-top: 54px;
                padding-bottom: 48px;
            }
            .catchpro-apply__grid,
            .catchpro-apply__flow,
            .catchpro-apply__fields,
            .catchpro-apply__contact {
                grid-template-columns: 1fr;
            }
        }
        @media (prefers-reduced-motion: reduce) {
            .catchpro-apply *,
            .catchpro-apply *::before,
            .catchpro-apply *::after {
                animation-duration: 0.01ms !important;
                animation-iteration-count: 1 !important;
                scroll-behavior: auto !important;
                transition-duration: 0.01ms !important;
            }
            .catchpro-apply__eyebrow,
            .catchpro-apply__hero h1,
            .catchpro-apply__lead,
            .catchpro-apply__actions,
            .catchpro-apply__panel,
            .catchpro-apply__step,
            .catchpro-apply__form,
            .catchpro-apply__contact {
                opacity: 1 !important;
                transform: none !important;
            }
        }
    </style>
    <div class="catchpro-apply">
        <section class="catchpro-apply__hero">
            <p class="catchpro-apply__eyebrow">CatchPro Pro</p>
            <h1>운행에 필요한 기능만 골라 더 빠르게 시작하세요.</h1>
            <p class="catchpro-apply__lead">
                인성 CatchPro Pro와 CatchPro Navi Pro는 사용하는 폰과 운행 방식에 따라 다르게 세팅됩니다.
                신청서를 남겨주시면 설치 가능 여부와 사용 방법을 순서대로 안내해드립니다.
            </p>
            <div class="catchpro-apply__actions">
                <a class="catchpro-apply__button" href="#catchpro-apply-form">신청서 작성</a>
                <?php if ($has_kakao) : ?>
                    <a class="catchpro-apply__button catchpro-apply__button--kakao" href="<?php echo esc_url($kakao_url); ?>" target="_blank" rel="noopener noreferrer">카카오톡 상담</a>
                <?php else : ?>
                    <span class="catchpro-apply__button catchpro-apply__button--disabled">카카오톡 상담 준비 중</span>
                <?php endif; ?>
                <a class="catchpro-apply__button catchpro-apply__button--ghost" href="#catchpro-apply-process">진행 방식 보기</a>
            </div>
        </section>

        <div class="catchpro-apply__wrap">
            <?php if ($is_done) : ?>
                <div class="catchpro-apply__notice">신청이 접수되었습니다. 확인 후 순서대로 연락드리겠습니다.</div>
            <?php endif; ?>

            <section aria-labelledby="catchpro-fit-title">
                <h2 id="catchpro-fit-title" class="catchpro-apply__section-title">서비스 구성</h2>
                <p class="catchpro-apply__section-copy">
                    오더를 잡는 폰과 지도를 보는 폰의 역할을 분리하면 운행 중 화면 전환이 줄어듭니다.
                    필요한 기능을 확인한 뒤 가장 맞는 구성으로 안내합니다.
                </p>
                <div class="catchpro-apply__grid">
                    <article class="catchpro-apply__panel">
                        <span class="catchpro-apply__feature-label">오더확정</span>
                        <h3>인성 CatchPro Pro</h3>
                        <p>인성앱을 사용하는 폰에서 조건 확인과 <span class="catchpro-apply__accent">오더확정</span> 동작을 빠르게 보조합니다.</p>
                    </article>
                    <article class="catchpro-apply__panel">
                        <span class="catchpro-apply__feature-label">방문순서</span>
                        <h3>CatchPro Navi Pro</h3>
                        <p><span class="catchpro-apply__accent">주소 동기화</span>, <span class="catchpro-apply__accent">방문순서</span>, 네비 실행을 지도 중심 화면으로 제공합니다.</p>
                    </article>
                    <article class="catchpro-apply__panel">
                        <span class="catchpro-apply__feature-label">설치안내</span>
                        <h3>설치 상담</h3>
                        <p>사용 기기와 운행 지역을 확인한 뒤 <span class="catchpro-apply__accent">설치 순서</span>와 이용 방법을 안내합니다.</p>
                    </article>
                </div>
            </section>

            <section id="catchpro-apply-process" aria-labelledby="catchpro-process-title">
                <h2 id="catchpro-process-title" class="catchpro-apply__section-title">진행 방식</h2>
                <div class="catchpro-apply__flow">
                    <div class="catchpro-apply__step">
                        <strong>1. 신청</strong>
                        <span>운행 지역, 사용 기기, 필요한 버전을 남깁니다.</span>
                    </div>
                    <div class="catchpro-apply__step">
                        <strong>2. 확인</strong>
                        <span>사용 기기와 필요한 기능을 확인합니다.</span>
                    </div>
                    <div class="catchpro-apply__step">
                        <strong>3. 안내</strong>
                        <span>이용 방식과 설치 절차를 안내합니다.</span>
                    </div>
                    <div class="catchpro-apply__step">
                        <strong>4. 설치</strong>
                        <span>설치 후 기본 설정을 함께 확인합니다.</span>
                    </div>
                </div>
            </section>

            <section id="catchpro-apply-form" aria-labelledby="catchpro-form-title">
                <h2 id="catchpro-form-title" class="catchpro-apply__section-title">신청서</h2>
                <p class="catchpro-apply__section-copy">
                    운행 지역, 사용 기기, 필요한 기능을 남겨주세요. 확인 후 연락드리겠습니다.
                </p>
                <div class="catchpro-apply__contact">
                    <div>
                        <strong>바로 상담이 필요하면 카카오톡으로 문의할 수 있습니다.</strong>
                        <span>신청서를 함께 남겨주시면 기기와 버전 확인을 더 빠르게 진행할 수 있습니다.</span>
                    </div>
                    <?php if ($has_kakao) : ?>
                        <a class="catchpro-apply__contact-link" href="<?php echo esc_url($kakao_url); ?>" target="_blank" rel="noopener noreferrer">카카오톡 상담 열기</a>
                    <?php else : ?>
                        <span class="catchpro-apply__contact-link catchpro-apply__contact-link--disabled">상담 링크 준비 중</span>
                    <?php endif; ?>
                </div>
                <form class="catchpro-apply__form" method="post" action="<?php echo esc_url(admin_url('admin-post.php')); ?>">
                    <input type="hidden" name="action" value="catchpro_apply_submit">
                    <?php wp_nonce_field('catchpro_apply_submit', 'catchpro_apply_nonce'); ?>
                    <div class="catchpro-apply__hidden" aria-hidden="true">
                        <label>Website <input type="text" name="catchpro_apply_website" value=""></label>
                    </div>
                    <div class="catchpro-apply__fields">
                        <div class="catchpro-apply__field">
                            <label for="catchpro-name">이름</label>
                            <input id="catchpro-name" name="name" type="text" autocomplete="name" required>
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-phone">연락처</label>
                            <input id="catchpro-phone" name="phone" type="tel" autocomplete="tel" placeholder="010-0000-0000" required>
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-email">이메일</label>
                            <input id="catchpro-email" name="email" type="email" autocomplete="email" required>
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-device">사용 기기</label>
                            <input id="catchpro-device" name="device" type="text" placeholder="예: 갤럭시 S24, 탭, 보조폰">
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-version">희망 버전</label>
                            <select id="catchpro-version" name="version" required>
                                <option value="">선택</option>
                                <option value="인성 CatchPro Pro">인성 CatchPro Pro</option>
                                <option value="CatchPro Navi Pro">CatchPro Navi Pro</option>
                                <option value="둘 다 상담">둘 다 상담</option>
                                <option value="아직 모름">아직 모름</option>
                            </select>
                        </div>
                        <div class="catchpro-apply__field">
                            <label for="catchpro-region">주 운행 지역</label>
                            <input id="catchpro-region" name="region" type="text" placeholder="예: 수원, 용인, 오산, 이천">
                        </div>
                        <div class="catchpro-apply__field catchpro-apply__field--wide">
                            <label for="catchpro-message">문의 내용</label>
                            <textarea id="catchpro-message" name="message" placeholder="현재 사용하는 방식, 필요한 기능, 궁금한 점을 적어주세요."></textarea>
                        </div>
                    </div>
                    <label class="catchpro-apply__consent">
                        <input type="checkbox" name="privacy_consent" value="1" required>
                        <span>상담 및 설치 안내를 위해 입력한 정보를 확인하는 것에 동의합니다.</span>
                    </label>
                    <button class="catchpro-apply__submit" type="submit">신청 접수하기</button>
                    <p class="catchpro-apply__fine">접수 후 순서대로 확인해 연락드립니다.</p>
                </form>
            </section>
        </div>
    </div>
    <script>
        (function () {
            var panels = document.querySelectorAll('.catchpro-apply__panel');
            if (!panels.length) {
                return;
            }

            if (!('IntersectionObserver' in window)) {
                panels.forEach(function (panel) {
                    panel.classList.add('is-visible');
                });
                return;
            }

            var observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                    if (!entry.isIntersecting) {
                        return;
                    }
                    entry.target.classList.add('is-visible');
                    observer.unobserve(entry.target);
                });
            }, {
                rootMargin: '0px 0px -10% 0px',
                threshold: 0.2
            });

            panels.forEach(function (panel) {
                observer.observe(panel);
            });
        })();
    </script>
    <?php
    return (string) ob_get_clean();
}

function catchpro_apply_handle_submit(): void
{
    if (!isset($_POST['catchpro_apply_nonce']) || !wp_verify_nonce(sanitize_text_field(wp_unslash($_POST['catchpro_apply_nonce'])), 'catchpro_apply_submit')) {
        wp_die('신청 검증에 실패했습니다. 페이지를 새로고침한 뒤 다시 시도해주세요.', 'CatchPro 신청', ['response' => 403]);
    }

    if (!empty($_POST['catchpro_apply_website'])) {
        wp_safe_redirect(home_url('/catchpro-pro-apply/'));
        exit;
    }

    if (empty($_POST['privacy_consent'])) {
        wp_die('개인정보 수집 동의가 필요합니다.', 'CatchPro 신청', ['response' => 400]);
    }

    $name = catchpro_apply_field('name');
    $phone = catchpro_apply_field('phone');
    $email = sanitize_email(catchpro_apply_field('email'));
    $device = catchpro_apply_field('device');
    $version = catchpro_apply_field('version');
    $region = catchpro_apply_field('region');
    $message = catchpro_apply_textarea('message');

    if ($name === '' || $phone === '' || $email === '' || $version === '') {
        wp_die('필수 항목을 입력해주세요.', 'CatchPro 신청', ['response' => 400]);
    }

    $summary = [
        '이름: ' . $name,
        '연락처: ' . $phone,
        '이메일: ' . $email,
        '사용 기기: ' . ($device !== '' ? $device : '-'),
        '희망 버전: ' . $version,
        '주 운행 지역: ' . ($region !== '' ? $region : '-'),
        '',
        '문의 내용:',
        $message !== '' ? $message : '-',
    ];

    $post_id = wp_insert_post([
        'post_type' => 'catchpro_application',
        'post_status' => 'private',
        'post_title' => sprintf('[%s] %s / %s', wp_date('Y-m-d H:i'), $name, $version),
        'post_content' => implode("\n", $summary),
    ], true);

    if (is_wp_error($post_id)) {
        wp_die('신청 저장에 실패했습니다. 잠시 후 다시 시도해주세요.', 'CatchPro 신청', ['response' => 500]);
    }

    update_post_meta($post_id, 'catchpro_name', $name);
    update_post_meta($post_id, 'catchpro_phone', $phone);
    update_post_meta($post_id, 'catchpro_email', $email);
    update_post_meta($post_id, 'catchpro_device', $device);
    update_post_meta($post_id, 'catchpro_version', $version);
    update_post_meta($post_id, 'catchpro_region', $region);
    update_post_meta($post_id, 'catchpro_submitted_ip_hash', wp_hash($_SERVER['REMOTE_ADDR'] ?? ''));

    $target = wp_get_referer();
    if (!$target) {
        $target = home_url('/catchpro-pro-apply/');
    }

    wp_safe_redirect(add_query_arg('catchpro_apply', 'success', $target) . '#catchpro-apply-form');
    exit;
}
