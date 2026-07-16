import sys

filepath = 'src/main/java/com/oculus/jj/ui/BrowserToolbar.java'
c = open(filepath).read()
sub = '(second) - BEFORE filter'
i = c.find(sub)

nc = '\n        // === Movie Playback (HOROS: MovieToolbarItemIdentifier 13th default, before flex space) ===\n'
nc += '        addSeparator();\n'
nc += '        JButton moviePlaybackBtn = new JButton(LanguageManager.get("btn_browser.movie_playback", "Movie Play"));\n'
nc += '        moviePlaybackBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));\n'
nc += '        moviePlaybackBtn.setToolTipText(LanguageManager.get("tt_browser.toolbar.moviePlayback", "Play/Pause DICOM movie playback"));\n'
nc += '        moviePlaybackBtn.addActionListener(e -> {\n'
nc += '            MoviePlaybackPanel.showMoviePanel(BrowserController.this, getActiveDCMViewForEdit());\n'
nc += '        });\n'
nc += '        add(moviePlaybackBtn);\n\n'

final = c[:i] + nc + sub + c[i+len(sub):]
open(filepath, 'w').write(final)
print('OK: Movie Playback button added at index', i)
